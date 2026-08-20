import { describe, expect, it } from 'vitest'
import { formatDecimal, isDecimal, parseDecimal, raiseBy } from './decimals'

describe('parseDecimal', () => {
  it.each([['75,50', 75.5], ['75.50', 75.5], ['6', 6], ['0,05', 0.05]])(
    'reads %s as %s', (input, expected) => {
      expect(parseDecimal(input)).toBe(expected)
    })

  it.each(['', ' ', 'abc', '75,5,0', '-3', '1e3'])('refuses %s rather than guessing', input => {
    expect(() => parseDecimal(input)).toThrow(/Bir sayı değil/)
  })

  it('is what stops NaN reaching a price list', () => {
    // Number('75,50') is NaN, and a NaN cost is a line item priced at nothing.
    expect(Number('75,50')).toBeNaN()
    expect(isDecimal('75,50')).toBe(true)
    expect(isDecimal('yetmişbeş')).toBe(false)
  })
})

describe('formatDecimal', () => {
  it('writes two decimals with a comma, as the price list is read', () => {
    expect(formatDecimal(71.3)).toBe('71,30')
    expect(formatDecimal(1900)).toBe('1900,00')
    expect(formatDecimal(6, 1)).toBe('6,0')
  })
})

describe('raiseBy', () => {
  it('rounds the way the server rounds, so the preview is the result', () => {
    expect(raiseBy(62, 15)).toBe(71.3)
    // The case the backend test pins: 115.00 at 1.5% is 116.725, and round(x, 2) makes it 116.73.
    expect(raiseBy(115, 1.5)).toBe(116.73)
    expect(raiseBy(1900, 20)).toBe(2280)
  })

  it('handles a reduction, which is what a supplier deal looks like', () => {
    expect(raiseBy(100, -10)).toBe(90)
  })
})
