import { describe, expect, it, vi } from 'vitest'

import { processFrame, uploadFrame } from './framePipeline'

/**
 * §9's five steps, in the order §9 puts them (BOYA-41).
 *
 * The order is not stylistic. EXIF is read before the re-encode because the re-encode strips it, and
 * the quality score is measured on the resized pixels because that is what the analysis will actually
 * be looking at — scoring the original would pass frames that lose their detail on the way down.
 *
 * Decoding and rasterising are the one part that needs a real browser, so they are a single injected
 * seam. Everything around them — which size, which measurements, what the verdict is, what gets sent —
 * is ordinary logic and is tested here in plain node.
 */

const JPEG = new Uint8Array([0xFF, 0xD8, 0xFF, 0xD9])

function file(bytes: Uint8Array<ArrayBuffer> = JPEG): Blob {
  return new Blob([bytes], { type: 'image/jpeg' })
}

/** Pixels of one flat colour: no edges, so a variance of zero — a deliberately blurred frame. */
function flatPixels(width: number, height: number, level = 120): Uint8ClampedArray {
  const pixels = new Uint8ClampedArray(width * height * 4)
  pixels.fill(level)
  for (let i = 3; i < pixels.length; i += 4) {
    pixels[i] = 255
  }
  return pixels
}

/** Alternating columns: plenty of edges, so a high variance. */
function sharpPixels(width: number, height: number): Uint8ClampedArray {
  const pixels = new Uint8ClampedArray(width * height * 4)
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const level = x % 2 === 0 ? 0 : 255
      const i = (y * width + x) * 4
      pixels[i] = level
      pixels[i + 1] = level
      pixels[i + 2] = level
      pixels[i + 3] = 255
    }
  }
  return pixels
}

function deps(overrides: Partial<Parameters<typeof processFrame>[3]> = {}) {
  return {
    decode: vi.fn(async () => ({ width: 4032, height: 3024 })),
    render: vi.fn(async (_f: Blob, size: { width: number, height: number }) => ({
      blob: new Blob([new Uint8Array(1024)], { type: 'image/jpeg' }),
      pixels: sharpPixels(size.width, size.height),
      width: size.width,
      height: size.height,
    })),
    offsetMinutes: () => 180,
    ...overrides,
  }
}

describe('processFrame', () => {
  it('resizes to the edge the role asks for, and reports the size it actually produced', async () => {
    const d = deps()

    const frame = await processFrame(file(), 'WALL_1', 1, d)

    expect(d.render).toHaveBeenCalledWith(expect.anything(), { width: 2048, height: 1536 })
    expect(frame.measurements.width).toBe(2048)
    expect(frame.measurements.height).toBe(1536)
  })

  it('gives a DETAIL close-up the larger edge §2.6 asks for', async () => {
    const d = deps()

    await processFrame(file(), 'DETAIL', 1, d)

    expect(d.render).toHaveBeenCalledWith(expect.anything(), { width: 2560, height: 1920 })
  })

  it('reads EXIF from the original, before the re-encode that would have destroyed it', async () => {
    const d = deps({ decode: vi.fn(async () => ({ width: 100, height: 100 })) })
    const readOrder: string[] = []
    const withOrder = {
      ...d,
      decode: vi.fn(async () => {
        readOrder.push('decode')
        return { width: 100, height: 100 }
      }),
      render: vi.fn(async (_f: Blob, size: { width: number, height: number }) => {
        readOrder.push('render')
        return { blob: new Blob([new Uint8Array(8)]), pixels: flatPixels(size.width, size.height), ...size }
      }),
    }

    await processFrame(file(), 'WALL_1', 1, withOrder)

    // The EXIF read happens against the file, so the only ordering that can go wrong is re-encoding
    // first and reading second. If render ever precedes the read, capturedAt is silently always null.
    expect(readOrder[0]).toBe('decode')
  })

  it('measures sharpness on the resized pixels, which is what the analysis will see', async () => {
    const frame = await processFrame(file(), 'WALL_1', 1, deps({
      decode: vi.fn(async () => ({ width: 900, height: 900 })),
      render: vi.fn(async (_f: Blob, size: { width: number, height: number }) => ({
        blob: new Blob([new Uint8Array(64)]),
        pixels: sharpPixels(size.width, size.height),
        ...size,
      })),
    }))

    expect(Number(frame.measurements.qualityScore)).toBeGreaterThan(1000)
    expect(frame.verdict.accept).toBe(true)
  })

  it('asks for a retake when the resized frame has no detail in it', async () => {
    const frame = await processFrame(file(), 'WALL_1', 1, deps({
      render: vi.fn(async (_f: Blob, size: { width: number, height: number }) => ({
        blob: new Blob([new Uint8Array(64)]),
        pixels: flatPixels(size.width, size.height),
        ...size,
      })),
    }))

    expect(frame.verdict.accept).toBe(false)
    expect(frame.verdict.reason).toBe('BLURRY')
  })

  it('keeps the fourth attempt at the same frame, flagged, rather than arguing again', async () => {
    const blurred = deps({
      render: vi.fn(async (_f: Blob, size: { width: number, height: number }) => ({
        blob: new Blob([new Uint8Array(64)]),
        pixels: flatPixels(size.width, size.height),
        ...size,
      })),
    })

    const frame = await processFrame(file(), 'WALL_1', 4, blurred)

    expect(frame.verdict.accept).toBe(true)
    expect(frame.measurements.lowQualityFlag).toBe(true)
  })

  it('reports the size of the re-encoded blob, not of the original the phone handed us', async () => {
    const frame = await processFrame(file(new Uint8Array(9_000_000)), 'WALL_1', 1, deps())

    expect(frame.measurements.byteSize).toBe(1024)
  })

  it('still produces a frame when the pixels cannot be read back', async () => {
    // Canvas readback is blocked or out of memory on some old devices. §9 would rather have the
    // photograph than the numbers, so the score goes null and the frame is accepted.
    const frame = await processFrame(file(), 'WALL_1', 1, deps({
      render: vi.fn(async (_f: Blob, size: { width: number, height: number }) => ({
        blob: new Blob([new Uint8Array(64)]),
        pixels: null,
        ...size,
      })),
    }))

    expect(frame.measurements.qualityScore).toBeUndefined()
    expect(frame.verdict.accept).toBe(true)
  })

  it('falls back to the original file when the frame cannot be decoded at all', async () => {
    const original = file(new Uint8Array(2048))

    const frame = await processFrame(original, 'WALL_1', 1, deps({
      decode: vi.fn(async () => {
        throw new Error('no decoder')
      }),
    }))

    // A frame we cannot process is still a frame the customer took, and the upload URL is already
    // signed. Sending the original unmeasured beats losing it.
    expect(frame.blob).toBe(original)
    expect(frame.measurements.qualityScore).toBeUndefined()
    expect(frame.verdict.accept).toBe(true)
  })
})

describe('uploadFrame', () => {
  function transport() {
    const calls: { url: string, blob: Blob, type: string }[] = []
    let handlers: { progress?: (sent: number, total: number) => void, done?: (status: number) => void,
      failed?: () => void } = {}
    return {
      calls,
      fire: () => handlers,
      send: vi.fn((url: string, blob: Blob, type: string, h: typeof handlers) => {
        calls.push({ url, blob, type })
        handlers = h
      }),
    }
  }

  it('PUTs the re-encoded bytes to the presigned URL as a JPEG', async () => {
    const t = transport()
    const blob = new Blob([new Uint8Array(16)], { type: 'image/jpeg' })

    const upload = uploadFrame('https://storage.test/put', blob, { transport: { send: t.send } })
    t.fire().done?.(200)
    await upload

    expect(t.calls[0]).toMatchObject({ url: 'https://storage.test/put', type: 'image/jpeg' })
  })

  it('reports progress so §2.7 can show the upload running while the customer walks on', async () => {
    const t = transport()
    const seen: number[] = []

    const upload = uploadFrame('https://storage.test/put', new Blob([new Uint8Array(4)]), {
      transport: { send: t.send },
      onProgress: fraction => seen.push(fraction),
    })
    t.fire().progress?.(50, 200)
    t.fire().progress?.(200, 200)
    t.fire().done?.(200)
    await upload

    expect(seen).toEqual([0.25, 1])
  })

  it('rejects on a status storage did not accept, so the queue can retry it', async () => {
    const t = transport()

    const upload = uploadFrame('https://storage.test/put', new Blob([new Uint8Array(4)]), { transport: { send: t.send } })
    t.fire().done?.(403)

    await expect(upload).rejects.toThrow(/403/)
  })

  it('still uses the real transport when only a progress callback is given', async () => {
    // The regression this exists for: onProgress used to live on the transport object, so asking for
    // progress meant passing a transport, and passing a partial one silently replaced the real XHR
    // with nothing. Every test passed a transport, so no test noticed; a browser did.
    const sent: { url: string, type: string | null }[] = []
    let load: (() => void) | null = null
    let progress: ((event: { loaded: number, total: number }) => void) | null = null

    class FakeXhr {
      status = 200
      upload = { addEventListener: (_t: string, h: typeof progress) => { progress = h } }
      open(_method: string, url: string) { sent.push({ url, type: null }) }
      setRequestHeader(_name: string, value: string) { sent[sent.length - 1]!.type = value }
      addEventListener(type: string, h: () => void) { if (type === 'load') { load = h } }
      send() {}
    }
    vi.stubGlobal('XMLHttpRequest', FakeXhr)

    const seen: number[] = []
    const upload = uploadFrame('https://storage.test/put', new Blob([new Uint8Array(4)]), {
      onProgress: fraction => seen.push(fraction),
    })
    progress!({ loaded: 3, total: 4 })
    load!()
    await upload

    expect(sent).toEqual([{ url: 'https://storage.test/put', type: 'image/jpeg' }])
    expect(seen).toEqual([0.75])
    vi.unstubAllGlobals()
  })

  it('rejects when the connection drops rather than resolving quietly', async () => {
    const t = transport()

    const upload = uploadFrame('https://storage.test/put', new Blob([new Uint8Array(4)]), { transport: { send: t.send } })
    t.fire().failed?.()

    await expect(upload).rejects.toThrow()
  })
})
