import { describe, expect, it } from 'vitest'

import { clockText, remainingFraction } from './codeExpiry'

/**
 * The clock on the code (§3.1, §11, BOYA-45).
 *
 * The reason it exists: an expired code and a mistyped one used to arrive on screen as the same
 * sentence, so somebody would retype the right digits and be told again that they were wrong. A clock
 * makes the difference visible before it happens.
 */
describe('clockText', () => {
  it('reads as a clock at five minutes and at nine seconds alike', () => {
    expect(clockText(300)).toBe('5:00')
    expect(clockText(9)).toBe('0:09')
  })

  it('pads the seconds, so 1:05 does not read as 1:5', () => {
    expect(clockText(65)).toBe('1:05')
  })

  it('rounds part-seconds up, so the last second is shown rather than skipped', () => {
    expect(clockText(0.2)).toBe('0:01')
  })

  it('stops at zero rather than counting into negatives', () => {
    expect(clockText(-30)).toBe('0:00')
  })
})

describe('remainingFraction', () => {
  it('is the share of the life that is left', () => {
    expect(remainingFraction(300, 150)).toBe(0.5)
  })

  it('is clamped at both ends: two clocks a second apart must not draw a bug', () => {
    expect(remainingFraction(300, 400)).toBe(1)
    expect(remainingFraction(300, -10)).toBe(0)
  })

  it('is zero rather than infinite when there was no life to begin with', () => {
    expect(remainingFraction(0, 10)).toBe(0)
  })
})
