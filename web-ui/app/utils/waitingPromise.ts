/**
 * Turning §8's instant into the sentence §3.2 asks for (BOYA-46).
 *
 * The server computes *when* — it is the only side that knows when the business opens, and §8 makes
 * the promise a working-hours calculation rather than an addition. What is left here is how to say it,
 * which is copy, and copy is the frontend's.
 *
 * §3.2 gives the requirement as the lie to avoid: at 23:00, "2 saat içinde" is false. The server has
 * already refused to produce that instant; this refuses to render one badly — "yarın saat 10:00" and
 * "bugün saat 14:00" are different sentences, and a customer who reads the wrong one waits on the
 * wrong day.
 *
 * Everything is rendered in Europe/Istanbul, whatever clock the phone is set to. The promise is about
 * when a person in Istanbul will pick the work up, and a customer abroad reading their own midnight
 * would be told something true about a moment and useless about a business.
 */
const ZONE = 'Europe/Istanbul'

export type PromiseWording = {
  /** A key under `waiting.` in tr.json. */
  key: 'promiseToday' | 'promiseTomorrow' | 'promise' | 'promiseUnknown'
  values: Record<string, string>
}

export function promiseWording(respondBy: string | null | undefined, now: Date): PromiseWording {
  if (!respondBy) {
    // A bookmark, or a reload after the answer was lost. Saying nothing about the time is honest;
    // inventing one is the failure §3.2 is about.
    return { key: 'promiseUnknown', values: {} }
  }

  const when = new Date(respondBy)
  if (Number.isNaN(when.getTime())) {
    return { key: 'promiseUnknown', values: {} }
  }

  const time = new Intl.DateTimeFormat('tr-TR', {
    timeZone: ZONE, hour: '2-digit', minute: '2-digit',
  }).format(when)

  const days = dayDifference(now, when)
  if (days === 0) {
    return { key: 'promiseToday', values: { time } }
  }
  if (days === 1) {
    return { key: 'promiseTomorrow', values: { time } }
  }

  const date = new Intl.DateTimeFormat('tr-TR', {
    timeZone: ZONE, day: 'numeric', month: 'long',
  }).format(when)
  return { key: 'promise', values: { when: `${date} ${time}` } }
}

/** Calendar days apart in Istanbul — not hours apart, which would call 01:00 tonight "today". */
function dayDifference(from: Date, to: Date): number {
  return Math.round((istanbulMidnight(to) - istanbulMidnight(from)) / 86_400_000)
}

function istanbulMidnight(at: Date): number {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: ZONE, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(at)
  return Date.parse(`${parts}T00:00:00Z`)
}
