/**
 * How long is left before the customer may ask for another code (§11, BOYA-45).
 *
 * A static "1 dakika sonra tekrar deneyin" is a sentence that stops being true the second it is
 * rendered, and a customer reading it has no way to know whether the minute has passed except by
 * pressing the button and being refused again. A number that moves answers the question it raises.
 *
 * §11 has three limits with very different lengths — a minute, a day, an hour — so the wording changes
 * with the scale. A mm:ss clock counting down twenty-four hours is not information, and "24 saat" for
 * forty seconds is not either. The server says which it is; this decides how to say it.
 *
 * Returns a key and values rather than a string: the prose lives in tr.json like all other copy, and
 * only the digits are computed here.
 */
export type RetryWording = {
  /** A key under `verify.retry.` in tr.json. */
  key: 'seconds' | 'clock' | 'hours'
  values: Record<string, string | number>
}

export function retryWording(secondsLeft: number): RetryWording {
  const left = Math.max(0, Math.ceil(secondsLeft))

  if (left < 60) {
    return { key: 'seconds', values: { seconds: left } }
  }
  if (left < 3600) {
    const minutes = Math.floor(left / 60)
    const seconds = left % 60
    return { key: 'clock', values: { clock: `${minutes}:${String(seconds).padStart(2, '0')}` } }
  }
  // Rounded up: telling somebody "1 saat" when it is fifty-nine minutes and fifty seconds sends them
  // back a moment early, to be refused again.
  return { key: 'hours', values: { hours: Math.ceil(left / 3600) } }
}

/** Seconds still to wait, given when the wait ends. Never negative. */
export function secondsUntil(endsAt: number, now: number): number {
  return Math.max(0, (endsAt - now) / 1000)
}
