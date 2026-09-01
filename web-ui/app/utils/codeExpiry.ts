/**
 * The life left in a verification code (workflow §3.1, BOYA-45).
 *
 * §11 gives the code five minutes, and until now the customer could not see any of it: a code silently
 * stopped working and the screen said only that it was wrong. Two different things — a mistyped code
 * and an expired one — arriving as the same sentence is how somebody ends up typing the same six
 * digits three times.
 *
 * The expiry comes from the server with the send. Nothing here knows how long a code lives, on
 * purpose: the lifetime is configuration, and a clock that guessed it would drift from the code it is
 * counting the day that value changes.
 */

/** Always {@code m:ss}, however little is left: a clock that changes shape is a clock nobody reads. */
export function clockText(secondsLeft: number): string {
  const left = Math.max(0, Math.ceil(secondsLeft))
  const minutes = Math.floor(left / 60)
  const seconds = left % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

/**
 * How much of the code's life is left, 1 down to 0 — what the ring is drawn from.
 *
 * Clamped at both ends. A clock that swept past zero, or filled beyond full because two machines
 * disagree about the time by a second, would be a drawing of a bug.
 */
export function remainingFraction(totalSeconds: number, secondsLeft: number): number {
  if (!(totalSeconds > 0)) {
    return 0
  }
  return Math.min(1, Math.max(0, secondsLeft / totalSeconds))
}
