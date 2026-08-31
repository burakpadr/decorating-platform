import { describe, expect, it } from 'vitest'

import { targetSize } from './frameSize'

/**
 * §9 step 2: long edge 2048 px, 2560 px for a DETAIL frame (BOYA-41).
 *
 * The reason DETAIL is larger is §2.6: those close-ups are "muhtemelen en değerli kareler" because a
 * hairline crack is invisible in a wide shot. Resizing them to the same edge as a wall would throw away
 * the only thing they were taken for.
 */
describe('targetSize', () => {
  it('puts the long edge at 2048 for an ordinary frame, keeping the aspect ratio', () => {
    expect(targetSize(4032, 3024, 'WALL_1')).toEqual({ width: 2048, height: 1536 })
  })

  it('measures the long edge, not the width: a portrait frame scales by its height', () => {
    expect(targetSize(3024, 4032, 'CEILING')).toEqual({ width: 1536, height: 2048 })
  })

  it('gives a DETAIL frame 2560, because that is what it was taken for', () => {
    expect(targetSize(4032, 3024, 'DETAIL')).toEqual({ width: 2560, height: 1920 })
  })

  it('never upscales: a small photograph is passed through at its own size', () => {
    expect(targetSize(1200, 900, 'WALL_2')).toEqual({ width: 1200, height: 900 })
  })

  it('rounds to whole pixels, and never to zero', () => {
    const { width, height } = targetSize(4000, 3, 'WALL_1')

    expect(Number.isInteger(width)).toBe(true)
    expect(Number.isInteger(height)).toBe(true)
    expect(height).toBeGreaterThanOrEqual(1)
  })

  it('leaves a square frame square', () => {
    expect(targetSize(3000, 3000, 'WALL_1')).toEqual({ width: 2048, height: 2048 })
  })
})
