import { describe, expect, it } from 'vitest'

import { assessFrame, laplacianVariance, meanLuminance, toGrayscale } from './frameQuality'

/**
 * The on-the-phone quality check (§9 step 4, workflow §2.5, BOYA-41).
 *
 * §2.5 says why it runs here and not on the server: "Videoda bunu ancak saatler sonra, analiz
 * bittiğinde fark edersiniz. Fotoğrafta o an yakalanır ve müşteri hâlâ evin içindeyken düzeltilir."
 * The value is entirely in the customer still being in the room.
 *
 * Which makes the direction of every threshold the point. §9: "Rejecting a good photo is far more
 * costly than accepting a mediocre one — the user gets annoyed and abandons." So these tests are
 * mostly about what gets through, and the one that matters most is the last: after three rejections
 * the argument stops, whatever the score.
 */

/** An RGBA buffer of one flat colour — no edges anywhere, which is what a blurred frame looks like. */
function flat(width: number, height: number, level: number): Uint8ClampedArray {
  const data = new Uint8ClampedArray(width * height * 4)
  for (let i = 0; i < width * height; i++) {
    data[i * 4] = level
    data[i * 4 + 1] = level
    data[i * 4 + 2] = level
    data[i * 4 + 3] = 255
  }
  return data
}

/** Alternating columns: the sharpest thing an image of this size can be. */
function stripes(width: number, height: number): Uint8ClampedArray {
  const data = new Uint8ClampedArray(width * height * 4)
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const level = x % 2 === 0 ? 0 : 255
      const i = (y * width + x) * 4
      data[i] = level
      data[i + 1] = level
      data[i + 2] = level
      data[i + 3] = 255
    }
  }
  return data
}

describe('toGrayscale', () => {
  it('weights the channels the way an eye does, rather than averaging them', () => {
    const green = new Uint8ClampedArray([0, 255, 0, 255])

    // Rec. 601 luma: green carries most of the brightness, so a green pixel is far from mid-grey.
    expect(toGrayscale(green, 1, 1)[0]).toBeGreaterThan(128)
  })

  it('leaves a grey image at its own level', () => {
    expect(toGrayscale(flat(2, 2, 90), 2, 2)).toEqual(new Uint8ClampedArray([90, 90, 90, 90]))
  })
})

describe('laplacianVariance', () => {
  it('is zero for an image with no edges at all', () => {
    expect(laplacianVariance(toGrayscale(flat(8, 8, 120), 8, 8), 8, 8)).toBe(0)
  })

  it('is large for a sharp image', () => {
    expect(laplacianVariance(toGrayscale(stripes(8, 8), 8, 8), 8, 8)).toBeGreaterThan(1000)
  })

  it('is zero rather than NaN when there is no interior to measure', () => {
    expect(laplacianVariance(toGrayscale(flat(2, 2, 10), 2, 2), 2, 2)).toBe(0)
  })
})

describe('meanLuminance', () => {
  it('reads the level of a flat image back', () => {
    expect(meanLuminance(toGrayscale(flat(4, 4, 77), 4, 4))).toBeCloseTo(77, 5)
  })

  it('is zero for an empty buffer rather than NaN', () => {
    expect(meanLuminance(new Uint8ClampedArray(0))).toBe(0)
  })
})

describe('assessFrame', () => {
  const good = { variance: 500, luminance: 120, width: 2048, height: 1536 }

  it('accepts an ordinary frame without comment', () => {
    expect(assessFrame(good, 1)).toEqual({ accept: true, reason: null, lowQualityFlag: false })
  })

  it('accepts a mediocre one: the thresholds are loose on purpose', () => {
    expect(assessFrame({ ...good, variance: 60, luminance: 55 }, 1).accept).toBe(true)
  })

  it('asks again for a frame that is genuinely blurred', () => {
    const verdict = assessFrame({ ...good, variance: 4 }, 1)

    expect(verdict.accept).toBe(false)
    expect(verdict.reason).toBe('BLURRY')
  })

  it('asks again for a room that was photographed with the lights off', () => {
    expect(assessFrame({ ...good, luminance: 9 }, 1).reason).toBe('DARK')
  })

  it('asks again when the camera gave us far too few pixels', () => {
    expect(assessFrame({ ...good, width: 320, height: 240 }, 1).reason).toBe('SMALL')
  })

  it('names blur first when a frame is both dark and blurred: it is the one the customer can fix', () => {
    expect(assessFrame({ ...good, variance: 2, luminance: 5 }, 1).reason).toBe('BLURRY')
  })

  it('stops arguing after three rejections and keeps the frame, flagged', () => {
    const hopeless = { ...good, variance: 1, luminance: 2 }

    expect(assessFrame(hopeless, 3).accept).toBe(false)
    expect(assessFrame(hopeless, 4)).toEqual({
      accept: true, reason: null, lowQualityFlag: true,
    })
  })

  it('does not flag a frame that was accepted on its merits', () => {
    expect(assessFrame(good, 4).lowQualityFlag).toBe(false)
  })

  it('accepts when the measurements could not be taken at all', () => {
    // An old phone with no canvas readback. §9 would rather have the photograph than the numbers.
    expect(assessFrame({ variance: null, luminance: null, width: null, height: null }, 1)).toEqual({
      accept: true, reason: null, lowQualityFlag: false,
    })
  })
})
