import { describe, expect, it } from 'vitest'
import { isStale, versionAge } from './versionAge'

/**
 * The operator reads this on the price list screen (BOYA-21: "sürümün yaşı görünür"), and it is the
 * only thing on that screen that tells them whether the figures are current. Workflow §6 expects an
 * increase every quarter or so; a list quietly a year old is money.
 */
describe('versionAge', () => {
  const now = new Date('2026-08-20T12:00:00Z')

  it.each([
    ['2026-08-20T11:40:00Z', 'az önce'],
    ['2026-08-20T04:00:00Z', 'bugün'],
    ['2026-08-19T09:00:00Z', 'dün'],
    ['2026-08-17T12:00:00Z', '3 gün önce'],
    ['2026-08-06T12:00:00Z', '2 hafta önce'],
    ['2026-05-20T12:00:00Z', '3 ay önce'],
    ['2025-06-20T12:00:00Z', '1 yıl önce'],
  ])('renders %s as "%s"', (createdAt, expected) => {
    expect(versionAge(createdAt, now)).toBe(expected)
  })

  it('does not render a negative age when the browser clock runs behind the API', () => {
    expect(versionAge('2026-08-20T12:00:30Z', now)).toBe('az önce')
  })

  it('refuses a date it cannot read rather than rendering "NaN gün önce"', () => {
    expect(() => versionAge('not a date', now)).toThrow(/Unreadable version date/)
  })
})

describe('isStale', () => {
  const now = new Date('2026-08-20T12:00:00Z')

  it('flags a list older than the quarter §6 plans around', () => {
    expect(isStale('2026-05-01T12:00:00Z', now)).toBe(true)
  })

  it('leaves a list from this quarter alone', () => {
    expect(isStale('2026-07-01T12:00:00Z', now)).toBe(false)
  })
})
