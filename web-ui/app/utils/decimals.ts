/**
 * Money and durations as the operator types them, and as the API takes them.
 *
 * A Turkish keyboard produces `75,50`; JSON takes `75.5`. Sending `Number('75,50')` gives `NaN`, and a
 * NaN that reaches a price list is a line item priced at nothing — the exact failure the engine's
 * tests exist to prevent, arriving through the panel instead.
 */

/** Accepts `75,50` and `75.50`; refuses anything else rather than guessing. */
export function parseDecimal(input: string): number {
  const normalised = input.trim().replace(',', '.')

  if (!/^\d+(\.\d+)?$/.test(normalised)) {
    throw new Error(`Bir sayı değil: ${input}`)
  }
  return Number(normalised)
}

/** True when the field holds something the API would accept. Used to enable the save. */
export function isDecimal(input: string): boolean {
  try {
    parseDecimal(input)
    return true
  }
  catch {
    return false
  }
}

/** `71.3` becomes `71,30` — two decimals, comma, the way the price list is read and written. */
export function formatDecimal(value: number, fractionDigits = 2): string {
  return value.toFixed(fractionDigits).replace('.', ',')
}

/**
 * What a percentage would do to one figure, rounded the way the server rounds it: HALF_UP at two
 * decimals (§5.8, and `round(x, 2)` in the increase SQL). The panel shows this before anything is
 * written, so the figure on screen has to be the figure that lands — a preview that disagrees with the
 * result by a kuruş is worse than no preview.
 */
export function raiseBy(value: number, percent: number): number {
  const raised = value * (1 + percent / 100)
  return Math.round((raised + Number.EPSILON) * 100) / 100
}
