/**
 * How money reaches the screen. The only place that decides it.
 *
 * Amounts arrive from the API as decimals (`numeric(14,2)` on the backend). They are displayed whole:
 * a painting quote in kuruş reads as false precision on a figure that carries a ±12% band anyway.
 */

const TR = new Intl.NumberFormat('tr-TR', { maximumFractionDigits: 0 })

const CURRENCY = 'TL'

/** En dash with hair spaces — a hyphen reads as a minus sign next to figures. */
const RANGE_SEPARATOR = ' – '

/**
 * Rounds half up, matching the engine's `HALF_UP` (§5.8), so a figure never disagrees with the
 * quote it came from. `Math.round` is half-up for positives, which is the only case money takes here.
 */
function toWhole(amount: number): number {
  return Math.round(amount)
}

export function formatAmount(amount: number): string {
  return `${TR.format(toWhole(amount))} ${CURRENCY}`
}

/**
 * Renders a band as `68.000 – 86.000 TL`: one currency suffix, because repeating it makes the two
 * numbers harder to compare at a glance.
 *
 * Throws on an inverted band. Silently swapping the bounds would render a pricing bug as a
 * plausible-looking string, and the band width is exactly what the customer is being asked to trust.
 */
export function formatPriceRange(low: number, high: number): string {
  if (toWhole(low) > toWhole(high)) {
    throw new Error(`Inverted price band: low ${low} is above high ${high}`)
  }

  if (toWhole(low) === toWhole(high)) {
    return formatAmount(low)
  }

  return `${TR.format(toWhole(low))}${RANGE_SEPARATOR}${formatAmount(high)}`
}
