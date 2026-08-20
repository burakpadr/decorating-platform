/**
 * How old a price list version is, in words.
 *
 * Workflow §6 expects an increase roughly quarterly, so the question the operator actually has in
 * front of the list is "when did I last touch this" — and a timestamp makes them do the subtraction.
 * "3 ay önce" answers it; `2026-05-14T09:31:00Z` does not.
 *
 * Turkish does not pluralise after a numeral — "3 gün", never "3 günler" — so there is one form per
 * unit and no plural rules to get wrong.
 */

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR
const WEEK = 7 * DAY

/** Calendar-ish months and years: close enough for "how stale is this", and never wrong by a unit. */
const MONTH = 30 * DAY
const YEAR = 365 * DAY

export function versionAge(createdAt: string | Date, now: Date = new Date()): string {
  const created = createdAt instanceof Date ? createdAt : new Date(createdAt)
  const elapsed = now.getTime() - created.getTime()

  if (Number.isNaN(elapsed)) {
    throw new Error(`Unreadable version date: ${String(createdAt)}`)
  }
  // A version created moments ago by the operator who is looking at it — and clock skew between the
  // browser and the API, which would otherwise render as "-1 gün önce".
  if (elapsed < HOUR) {
    return 'az önce'
  }
  if (elapsed < DAY) {
    return 'bugün'
  }
  if (elapsed < 2 * DAY) {
    return 'dün'
  }
  if (elapsed < WEEK) {
    return `${Math.floor(elapsed / DAY)} gün önce`
  }
  if (elapsed < MONTH) {
    return `${Math.floor(elapsed / WEEK)} hafta önce`
  }
  if (elapsed < YEAR) {
    return `${Math.floor(elapsed / MONTH)} ay önce`
  }
  return `${Math.floor(elapsed / YEAR)} yıl önce`
}

/**
 * Whether a version is old enough that the operator should be asked about it. Workflow §6 puts an
 * increase at roughly every three months, so a list older than that is the thing most likely to be
 * quietly costing money in an inflationary market.
 */
export function isStale(createdAt: string | Date, now: Date = new Date()): boolean {
  const created = createdAt instanceof Date ? createdAt : new Date(createdAt)
  return now.getTime() - created.getTime() >= 3 * MONTH
}
