/**
 * When the photograph was taken, read from the file before we destroy the evidence (§9 step 1, BOYA-41).
 *
 * §9 orders the pipeline deliberately: EXIF first, because step 3's re-encode strips it as a side
 * effect. Nothing else in the flow knows this — the upload time is when the bytes arrived, which is
 * after however long the customer spent walking the flat and however long the phone spent in a lift.
 *
 * Written by hand rather than pulled in as a dependency. What is needed is two ASCII tags out of one
 * segment, the parser is a page long, and the alternative is shipping a general-purpose metadata reader
 * to a phone on a Turkish mobile connection to answer a question this narrow.
 *
 * Every failure returns null. A photograph with unreadable metadata is still a photograph, and
 * `capturedAt` is nullable all the way to the column for exactly that reason.
 */

const SOI = 0xD8
const EOI = 0xD9
const APP1 = 0xE1
const SOS = 0xDA

const DATE_TIME_ORIGINAL = 0x9003
const OFFSET_TIME_ORIGINAL = 0x9011
const EXIF_IFD_POINTER = 0x8769

const ASCII = 2

/** `taken` is local and unzoned, as EXIF records it. `offset` is present only if the phone said so. */
export type ExifMoment = { taken: string, offset: string | null }

export function exifCapturedAt(bytes: Uint8Array): ExifMoment | null {
  try {
    return read(bytes)
  }
  catch {
    // A truncated upload, a file that is not what its name says, a segment length that walks off the
    // end. None of these is worth a thrown error on the capture screen.
    return null
  }
}

/**
 * The EXIF moment as an instant, or null.
 *
 * @param deviceOffsetMinutes minutes ahead of UTC where the phone is — `-new Date().getTimezoneOffset()`.
 *   Used only when the file carries no zone of its own, which is the common case: the device that took
 *   the photograph is the best evidence available of which local time that was, because it is the one
 *   that was in the room.
 */
export function capturedAtIso(moment: ExifMoment | null, deviceOffsetMinutes: number): string | null {
  if (moment === null) {
    return null
  }
  const minutes = moment.offset === null ? deviceOffsetMinutes : offsetMinutes(moment.offset)
  if (minutes === null) {
    return null
  }

  const local = Date.parse(`${moment.taken}Z`)
  if (Number.isNaN(local)) {
    return null
  }
  return new Date(local - minutes * 60_000).toISOString().replace(/\.\d{3}Z$/, 'Z')
}

function offsetMinutes(offset: string): number | null {
  const parts = /^([+-])(\d{2}):(\d{2})$/.exec(offset.trim())
  if (parts === null) {
    return null
  }
  const magnitude = Number(parts[2]) * 60 + Number(parts[3])
  return parts[1] === '-' ? -magnitude : magnitude
}

function read(bytes: Uint8Array): ExifMoment | null {
  if (bytes.length < 4 || bytes[0] !== 0xFF || bytes[1] !== SOI) {
    return null
  }
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)

  let p = 2
  while (p + 4 <= bytes.length) {
    if (bytes[p] !== 0xFF) {
      return null
    }
    const marker = bytes[p + 1]!
    if (marker === EOI || marker === SOS) {
      // Past here the file is compressed image data; metadata never appears after it.
      return null
    }
    const length = view.getUint16(p + 2, false)
    if (length < 2 || p + 2 + length > bytes.length) {
      return null
    }
    if (marker === APP1 && isExif(bytes, p + 4)) {
      return readTiff(view, bytes, p + 10, p + 2 + length)
    }
    p += 2 + length
  }
  return null
}

function isExif(bytes: Uint8Array, at: number): boolean {
  return bytes[at] === 0x45 && bytes[at + 1] === 0x78 && bytes[at + 2] === 0x69
    && bytes[at + 3] === 0x66 && bytes[at + 4] === 0x00 && bytes[at + 5] === 0x00
}

/** @param tiff where the TIFF header begins — every offset inside EXIF is relative to it. */
function readTiff(view: DataView, bytes: Uint8Array, tiff: number, end: number): ExifMoment | null {
  if (tiff + 8 > end) {
    return null
  }
  const little = bytes[tiff] === 0x49 && bytes[tiff + 1] === 0x49
  const big = bytes[tiff] === 0x4D && bytes[tiff + 1] === 0x4D
  if (!little && !big) {
    return null
  }
  if (view.getUint16(tiff + 2, little) !== 42) {
    return null
  }

  const ifd0 = tiff + view.getUint32(tiff + 4, little)
  const exifIfd = findEntry(view, ifd0, tiff, end, little, EXIF_IFD_POINTER)
  if (exifIfd === null) {
    return null
  }

  const at = tiff + Number(exifIfd.value)
  const taken = readAscii(view, bytes, tiff, end, little, at, DATE_TIME_ORIGINAL)
  const iso = taken === null ? null : isoDate(taken)
  if (iso === null) {
    return null
  }
  return { taken: iso, offset: readAscii(view, bytes, tiff, end, little, at, OFFSET_TIME_ORIGINAL) }
}

type Entry = { type: number, count: number, value: number, at: number }

function findEntry(view: DataView, ifd: number, tiff: number, end: number, little: boolean,
  tag: number): Entry | null {
  if (ifd + 2 > end) {
    return null
  }
  const count = view.getUint16(ifd, little)
  for (let i = 0; i < count; i++) {
    const at = ifd + 2 + i * 12
    if (at + 12 > end) {
      return null
    }
    if (view.getUint16(at, little) === tag) {
      return {
        type: view.getUint16(at + 2, little),
        count: view.getUint32(at + 4, little),
        value: view.getUint32(at + 8, little),
        at: at + 8,
      }
    }
  }
  return null
}

/** ASCII values of four bytes or fewer sit in the entry itself; longer ones are an offset. */
function readAscii(view: DataView, bytes: Uint8Array, tiff: number, end: number, little: boolean,
  ifd: number, tag: number): string | null {
  const entry = findEntry(view, ifd, tiff, end, little, tag)
  if (entry === null || entry.type !== ASCII || entry.count === 0) {
    return null
  }
  const start = entry.count <= 4 ? entry.at : tiff + entry.value
  const stop = start + entry.count
  if (start < 0 || stop > end) {
    return null
  }
  let text = ''
  for (let i = start; i < stop; i++) {
    const code = bytes[i]!
    if (code === 0) {
      break
    }
    text += String.fromCharCode(code)
  }
  return text.trim() === '' ? null : text.trim()
}

/** EXIF writes `YYYY:MM:DD HH:MM:SS`. Anything else is a phone we do not understand. */
function isoDate(text: string): string | null {
  const parts = /^(\d{4}):(\d{2}):(\d{2})[ T](\d{2}):(\d{2}):(\d{2})$/.exec(text)
  if (parts === null) {
    return null
  }
  return `${parts[1]}-${parts[2]}-${parts[3]}T${parts[4]}:${parts[5]}:${parts[6]}`
}
