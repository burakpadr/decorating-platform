import { describe, expect, it } from 'vitest'

import { retryWording, secondsUntil } from './retryCountdown'

/**
 * The countdown on a rate-limited send (§11, BOYA-45).
 *
 * §11's three limits are a minute, a day and an hour apart in scale, and the same wording cannot serve
 * all three: a mm:ss clock ticking down a day is not information, and "24 saat" for forty seconds is
 * not either. The server says how long; these tests are that the right shape of sentence is chosen.
 */
describe('retryWording', () => {
  it('counts plain seconds under a minute, which is the ordinary case', () => {
    expect(retryWording(45)).toEqual({ key: 'seconds', values: { seconds: 45 } })
  })

  it('switches to a clock once there are minutes to show', () => {
    expect(retryWording(119)).toEqual({ key: 'clock', values: { clock: '1:59' } })
  })

  it('pads the seconds, so 1:05 does not read as 1:5', () => {
    expect(retryWording(65).values.clock).toBe('1:05')
  })

  it('speaks in hours for §11\'s daily limit, where a ticking clock would be useless', () => {
    expect(retryWording(24 * 3600)).toEqual({ key: 'hours', values: { hours: 24 } })
  })

  it('rounds hours up: "1 saat" at 59 minutes sends somebody back a moment early', () => {
    expect(retryWording(3599 + 3600).values.hours).toBe(2)
  })

  it('never counts below zero', () => {
    expect(retryWording(-5)).toEqual({ key: 'seconds', values: { seconds: 0 } })
  })

  it('rounds part-seconds up, so the last one is shown rather than skipped', () => {
    expect(retryWording(0.4).values.seconds).toBe(1)
  })
})

describe('secondsUntil', () => {
  it('is the gap, in seconds', () => {
    expect(secondsUntil(10_000, 4_000)).toBe(6)
  })

  it('is zero once the moment has passed', () => {
    expect(secondsUntil(1_000, 9_000)).toBe(0)
  })
})
