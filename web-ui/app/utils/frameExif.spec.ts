import { describe, expect, it } from 'vitest'

import { capturedAtIso, exifCapturedAt } from './frameExif'

/**
 * Reading the moment a frame was taken (§9 step 1, BOYA-41).
 *
 * The order in §9's pipeline is the point: EXIF is read *before* step 3 re-encodes the frame, because
 * the re-encode strips it. What is being preserved is the difference between when the photograph was
 * taken and when it arrived — however long the customer spent walking the flat, plus however long the
 * upload sat in a lift with no signal.
 *
 * The fixtures below are real JPEG bytes rather than a mocked parser. A hand-rolled TIFF reader that
 * has only ever been tested against its own idea of the format is a reader that works until a phone
 * disagrees with it.
 */

/** A minimal but structurally valid JPEG carrying an APP1/Exif segment. */
function jpegWithExif(taken: string | null, offset: string | null): Uint8Array {
  const entries: { tag: number, text: string }[] = []
  if (taken !== null) {
    entries.push({ tag: 0x9003, text: taken })
  }
  if (offset !== null) {
    entries.push({ tag: 0x9011, text: offset })
  }

  // TIFF: header (8) + IFD0 with one pointer entry (2 + 12 + 4) + the Exif IFD itself.
  const exifIfdAt = 26
  const stringsAt = exifIfdAt + 2 + entries.length * 12 + 4
  const stringBytes = entries.map(e => new TextEncoder().encode(`${e.text}\0`))
  const tiffLength = stringsAt + stringBytes.reduce((total, s) => total + s.length, 0)

  const tiff = new DataView(new ArrayBuffer(tiffLength))
  const bytes = new Uint8Array(tiff.buffer)
  bytes[0] = 0x49
  bytes[1] = 0x49 // "II", little-endian
  tiff.setUint16(2, 42, true)
  tiff.setUint32(4, 8, true) // IFD0 begins here

  tiff.setUint16(8, 1, true) // one entry in IFD0: the pointer to the Exif IFD
  tiff.setUint16(10, 0x8769, true)
  tiff.setUint16(12, 4, true) // LONG
  tiff.setUint32(14, 1, true)
  tiff.setUint32(18, exifIfdAt, true)
  tiff.setUint32(22, 0, true) // no IFD1

  tiff.setUint16(exifIfdAt, entries.length, true)
  let cursor = exifIfdAt + 2
  let stringCursor = stringsAt
  entries.forEach((entry, index) => {
    const text = stringBytes[index]!
    tiff.setUint16(cursor, entry.tag, true)
    tiff.setUint16(cursor + 2, 2, true) // ASCII
    tiff.setUint32(cursor + 4, text.length, true)
    tiff.setUint32(cursor + 8, stringCursor, true)
    bytes.set(text, stringCursor)
    stringCursor += text.length
    cursor += 12
  })
  tiff.setUint32(cursor, 0, true)

  const payload = new Uint8Array(6 + tiffLength)
  payload.set(new TextEncoder().encode('Exif\0\0'), 0)
  payload.set(bytes, 6)

  // SOI (2) + APP1 marker (2) + segment length (2) before the payload, EOI (2) after it.
  const jpeg = new Uint8Array(6 + payload.length + 2)
  jpeg[0] = 0xFF
  jpeg[1] = 0xD8 // SOI
  jpeg[2] = 0xFF
  jpeg[3] = 0xE1 // APP1
  new DataView(jpeg.buffer).setUint16(4, payload.length + 2, false) // segment length is big-endian
  jpeg.set(payload, 6)
  jpeg[jpeg.length - 2] = 0xFF
  jpeg[jpeg.length - 1] = 0xD9 // EOI
  return jpeg
}

describe('exifCapturedAt', () => {
  it('reads DateTimeOriginal out of a JPEG', () => {
    const moment = exifCapturedAt(jpegWithExif('2026:08:31 14:05:09', null))

    expect(moment).toEqual({ taken: '2026-08-31T14:05:09', offset: null })
  })

  it('reads the zone the phone recorded, when it recorded one', () => {
    const moment = exifCapturedAt(jpegWithExif('2026:08:31 14:05:09', '+03:00'))

    expect(moment?.offset).toBe('+03:00')
  })

  it('is null for a file with no EXIF at all', () => {
    // What a canvas re-encode produces, and what a screenshot or a downloaded image usually is.
    expect(exifCapturedAt(new Uint8Array([0xFF, 0xD8, 0xFF, 0xD9]))).toBeNull()
  })

  it('is null rather than a guess for something that is not a JPEG', () => {
    expect(exifCapturedAt(new TextEncoder().encode('PNG or nothing at all'))).toBeNull()
  })

  it('is null for an EXIF block that has no DateTimeOriginal', () => {
    expect(exifCapturedAt(jpegWithExif(null, '+03:00'))).toBeNull()
  })

  it('refuses a malformed date rather than passing it on', () => {
    expect(exifCapturedAt(jpegWithExif('not a date at all', null))).toBeNull()
  })

  it('survives a truncated file without throwing', () => {
    const whole = jpegWithExif('2026:08:31 14:05:09', null)

    for (const cut of [8, 12, 20, 40, whole.length - 4]) {
      expect(() => exifCapturedAt(whole.slice(0, cut))).not.toThrow()
    }
  })
})

describe('capturedAtIso', () => {
  it('uses the zone from the file when there is one', () => {
    const iso = capturedAtIso({ taken: '2026-08-31T14:05:09', offset: '+03:00' }, -60)

    expect(iso).toBe('2026-08-31T11:05:09Z')
  })

  it('falls back to the zone of the phone doing the uploading', () => {
    // EXIF times are local and usually unzoned. The device that took the photograph is the best
    // evidence available of which local that was — it is the one in the room.
    const iso = capturedAtIso({ taken: '2026-08-31T14:05:09', offset: null }, 180)

    expect(iso).toBe('2026-08-31T11:05:09Z')
  })

  it('handles a negative offset without losing the day', () => {
    expect(capturedAtIso({ taken: '2026-08-31T01:30:00', offset: '-05:00' }, 0))
      .toBe('2026-08-31T06:30:00Z')
  })

  it('is null when there was nothing to read', () => {
    expect(capturedAtIso(null, 180)).toBeNull()
  })
})
