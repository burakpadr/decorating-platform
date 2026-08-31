import type { components } from '@decorating/api-client'

import { capturedAtIso, exifCapturedAt } from './frameExif'
import { assessFrame, laplacianVariance, meanLuminance, toGrayscale } from './frameQuality'
import { targetSize } from './frameSize'
import type { FrameSize } from './frameSize'
import type { FrameVerdict } from './frameQuality'

type PhotoRole = components['schemas']['UploadIntentRequest']['role']
type Measurements = components['schemas']['CompleteUploadRequest']

/**
 * §9's client-side pipeline: read, resize, re-encode, measure, upload (BOYA-41).
 *
 * The order in §9 is load-bearing twice over. EXIF is read first because step 3's re-encode strips it,
 * and the sharpness is measured on the *resized* pixels because those are what the analysis will be
 * given — a frame that looks sharp at 4032 px and mushy at 2048 is a frame we would otherwise accept
 * and then fail to read anything from.
 *
 * Nothing here decides what happens next. Whether a rejected frame is shown to the customer, and how
 * many times it has been tried, belongs to the capture screen (BOYA-42); whether a failed upload is
 * retried belongs to the queue (BOYA-44). This produces the bytes, the numbers and a verdict.
 *
 * Every measurement degrades to absent rather than failing. §9's trade runs one way — "rejecting a good
 * photo is far more costly than accepting a mediocre one" — and a phone too old to read its own canvas
 * back has still taken the photograph.
 */

/** JPEG quality for the re-encode. §9 names it: q85, which is where the artefacts stop being visible. */
const QUALITY = 0.85

export type RenderedFrame = {
  blob: Blob
  /** RGBA of the resized frame, or null where the canvas cannot be read back. */
  pixels: Uint8ClampedArray | null
  width: number
  height: number
}

/**
 * The one part that needs a browser, kept behind a single seam.
 *
 * Splitting it finer would put `HTMLCanvasElement` in the signature of everything above it and buy
 * nothing: decode, draw, read back and encode are one operation on one canvas, and a test that fakes
 * three quarters of it is testing its own fake.
 */
export type FrameTools = {
  decode: (file: Blob) => Promise<FrameSize>
  render: (file: Blob, size: FrameSize) => Promise<RenderedFrame>
  offsetMinutes: () => number
}

export type ProcessedFrame = {
  /** What to PUT. The re-encoded frame, or the original if it could not be processed. */
  blob: Blob
  measurements: Measurements
  verdict: FrameVerdict
}

/**
 * @param attempt 1 for the first go at this frame, incrementing on each retake of the same one — it is
 *   what stops §9's three-rejection rule from becoming an argument nobody wins.
 */
export async function processFrame(file: Blob, role: PhotoRole, attempt: number,
  tools: FrameTools = browserTools()): Promise<ProcessedFrame> {
  // Step 1, and it has to be first: after step 3 there is no EXIF left to read.
  const capturedAt = await readCapturedAt(file, tools)

  let rendered: RenderedFrame
  try {
    const source = await tools.decode(file)
    rendered = await tools.render(file, targetSize(source.width, source.height, role))
  }
  catch {
    // Nothing decoded it. The customer took a photograph and the upload URL is already signed, so send
    // what we have — unmeasured and unresized — rather than lose the frame over a codec.
    return {
      blob: file,
      measurements: { capturedAt, byteSize: file.size || undefined, lowQualityFlag: false },
      verdict: { accept: true, reason: null, lowQualityFlag: false },
    }
  }

  const measured = measure(rendered)
  const verdict = assessFrame({
    variance: measured.variance,
    luminance: measured.luminance,
    width: rendered.width,
    height: rendered.height,
  }, attempt)

  return {
    blob: rendered.blob,
    measurements: {
      capturedAt,
      width: rendered.width,
      height: rendered.height,
      byteSize: rendered.blob.size || undefined,
      // Sent unrounded and unclamped: `Photo` clamps it to what numeric(5,2) holds, and doing it in two
      // places would mean two definitions of the same number.
      qualityScore: measured.variance ?? undefined,
      lowQualityFlag: verdict.lowQualityFlag,
    },
    verdict,
  }
}

/**
 * Undefined rather than null throughout: the generated contract makes every measurement optional, so
 * an unread one is a field that is simply not sent. Jackson reads the absence as the null the column
 * already allows.
 */
async function readCapturedAt(file: Blob, tools: FrameTools): Promise<string | undefined> {
  try {
    const bytes = new Uint8Array(await file.arrayBuffer())
    return capturedAtIso(exifCapturedAt(bytes), tools.offsetMinutes()) ?? undefined
  }
  catch {
    return undefined
  }
}

function measure(rendered: RenderedFrame): { variance: number | null, luminance: number | null } {
  if (rendered.pixels === null) {
    return { variance: null, luminance: null }
  }
  const gray = toGrayscale(rendered.pixels, rendered.width, rendered.height)
  return {
    variance: laplacianVariance(gray, rendered.width, rendered.height),
    luminance: meanLuminance(gray),
  }
}

export type UploadTransport = {
  send: (url: string, blob: Blob, contentType: string, handlers: {
    progress?: (sent: number, total: number) => void
    done?: (status: number) => void
    failed?: () => void
  }) => void
}

/**
 * `onProgress` is separate from `transport` on purpose. They were one object at first, which meant the
 * ordinary case — a real upload that reports progress — could only be had by supplying the transport
 * too, and supplying a partial one silently disabled the real XHR. Found by running the pipeline in a
 * browser rather than by a test, because every test passed a transport.
 */
export type UploadOptions = {
  onProgress?: (fraction: number) => void
  transport?: UploadTransport
}

/**
 * §9 step 5: the bytes go straight to storage, never through the JVM.
 *
 * Progress is reported because §2.7 promises it — uploading starts the moment a frame is accepted and
 * continues while the customer walks to the next room, and an upload nobody can see is one people
 * interrupt by closing the tab.
 *
 * XHR rather than fetch, and that is the only reason: fetch still cannot report upload progress.
 */
export function uploadFrame(url: string, blob: Blob, options: UploadOptions = {}): Promise<void> {
  const transport = options.transport ?? xhrTransport()
  return new Promise((resolve, reject) => {
    transport.send(url, blob, 'image/jpeg', {
      progress: (sent, total) => {
        if (total > 0) {
          options.onProgress?.(sent / total)
        }
      },
      done: (status) => {
        if (status >= 200 && status < 300) {
          resolve()
          return
        }
        // Rejected rather than swallowed: the row is still PENDING server-side, and the queue that
        // retries this (BOYA-44) can only retry what it is told failed.
        reject(new Error(`storage refused the frame: ${status}`))
      },
      failed: () => reject(new Error('the connection dropped before the frame arrived')),
    })
  })
}

function xhrTransport(): UploadTransport {
  return {
    send(url, blob, contentType, handlers) {
      const request = new XMLHttpRequest()
      request.open('PUT', url, true)
      request.setRequestHeader('Content-Type', contentType)
      request.upload.addEventListener('progress', event => handlers.progress?.(event.loaded, event.total))
      request.addEventListener('load', () => handlers.done?.(request.status))
      request.addEventListener('error', () => handlers.failed?.())
      request.addEventListener('abort', () => handlers.failed?.())
      request.send(blob)
    },
  }
}

/**
 * The real canvas work.
 *
 * `createImageBitmap` is used where it exists because it decodes off the main thread — on a mid-range
 * Android holding a 12 megapixel frame, decoding on the main thread is a visible freeze in the middle
 * of a flow the customer is already halfway through.
 */
function browserTools(): FrameTools {
  return {
    async decode(file) {
      const bitmap = await createImageBitmap(file)
      const size = { width: bitmap.width, height: bitmap.height }
      bitmap.close()
      return size
    },

    async render(file, size) {
      const bitmap = await createImageBitmap(file)
      try {
        const canvas = document.createElement('canvas')
        canvas.width = size.width
        canvas.height = size.height
        const context = canvas.getContext('2d')
        if (context === null) {
          throw new Error('no 2d context')
        }
        context.drawImage(bitmap, 0, 0, size.width, size.height)

        return {
          blob: await encode(canvas),
          pixels: readBack(context, size),
          width: size.width,
          height: size.height,
        }
      }
      finally {
        bitmap.close()
      }
    },

    offsetMinutes: () => -new Date().getTimezoneOffset(),
  }
}

/** Reading a canvas back can throw or run out of memory; the frame survives either way. */
function readBack(context: CanvasRenderingContext2D, size: FrameSize): Uint8ClampedArray | null {
  try {
    return context.getImageData(0, 0, size.width, size.height).data
  }
  catch {
    return null
  }
}

function encode(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      blob => (blob === null ? reject(new Error('the frame could not be re-encoded')) : resolve(blob)),
      'image/jpeg',
      QUALITY,
    )
  })
}
