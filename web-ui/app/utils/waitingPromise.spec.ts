import { describe, expect, it } from 'vitest'

import { promiseWording } from './waitingPromise'

/**
 * How the promise is said (§3.2, BOYA-46).
 *
 * §3.2's rule is about a sentence, not a number: "Gece 23:00'te gelen talebe '2 saat içinde' demek
 * yalan olur; 'yarın sabah 10:00'a kadar' denir." The server refuses to produce the wrong instant;
 * these tests are that the right instant is not then read out as the wrong day.
 *
 * Everything is asserted in Europe/Istanbul, because that is the clock the promise is about — the one
 * the person doing the work is looking at.
 */
describe('promiseWording', () => {
  const at = (iso: string) => new Date(iso)

  it('says today, with the hour, for a promise later the same day', () => {
    // 11:00Z is 14:00 in Istanbul.
    const said = promiseWording('2026-09-01T11:00:00Z', at('2026-09-01T07:00:00Z'))

    expect(said.key).toBe('promiseToday')
    expect(said.values.time).toBe('14:00')
  })

  it('says tomorrow for §3.2\'s own example: asked at 23:00, answered in the morning', () => {
    // 20:00Z on the 1st is 23:00 in Istanbul; 08:00Z on the 2nd is 11:00.
    const said = promiseWording('2026-09-02T08:00:00Z', at('2026-09-01T20:00:00Z'))

    expect(said.key).toBe('promiseTomorrow')
    expect(said.values.time).toBe('11:00')
  })

  it('names the date when it is further out than tomorrow', () => {
    const said = promiseWording('2026-09-04T08:00:00Z', at('2026-09-01T07:00:00Z'))

    expect(said.key).toBe('promise')
    expect(said.values.when).toContain('4 Eylül')
  })

  it('counts calendar days in Istanbul, not hours: 01:00 tonight is tomorrow', () => {
    // 22:00Z on the 1st is 01:00 on the 2nd in Istanbul — three hours away, but a different day.
    const said = promiseWording('2026-09-01T22:00:00Z', at('2026-09-01T19:00:00Z'))

    expect(said.key).toBe('promiseTomorrow')
  })

  it('says nothing about the time when there is nothing to say', () => {
    expect(promiseWording(null, at('2026-09-01T07:00:00Z')).key).toBe('promiseUnknown')
    expect(promiseWording(undefined, at('2026-09-01T07:00:00Z')).key).toBe('promiseUnknown')
    expect(promiseWording('not a date', at('2026-09-01T07:00:00Z')).key).toBe('promiseUnknown')
  })

  it('renders in Istanbul whatever the phone is set to', () => {
    // A customer reading this in London still needs the hour a decorator in Kadıköy will start.
    const said = promiseWording('2026-09-01T06:30:00Z', at('2026-09-01T05:00:00Z'))

    expect(said.values.time).toBe('09:30')
  })
})
