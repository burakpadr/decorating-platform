import { describe, expect, it } from 'vitest'
import { formatPriceRange, formatAmount } from './money'

/*
 * Stage 1 shows the customer a range, never a single figure (§5.9): low confidence widens the band
 * and the width is the honest part of the answer. These helpers are the only place that decides how
 * money reaches the screen.
 */
describe('formatAmount', () => {
  it('groups thousands the Turkish way and appends the currency', () => {
    expect(formatAmount(68000)).toBe('68.000 TL')
  })

  it('drops kuruş — quotes are never shown to two decimals', () => {
    expect(formatAmount(68276.49)).toBe('68.276 TL')
  })

  it('rounds half up, matching the engine', () => {
    expect(formatAmount(1500.5)).toBe('1.501 TL')
  })
})

describe('formatPriceRange', () => {
  it('renders a band with a single currency suffix', () => {
    expect(formatPriceRange(68000, 86000)).toBe('68.000 – 86.000 TL')
  })

  it('collapses to one figure when the band has no width', () => {
    expect(formatPriceRange(70000, 70000)).toBe('70.000 TL')
  })

  it('refuses an inverted band rather than silently swapping it', () => {
    // A band low above its high means the engine produced nonsense. Rendering it politely would
    // hide a pricing bug behind a plausible-looking string.
    expect(() => formatPriceRange(86000, 68000)).toThrow(/inverted/i)
  })
})
