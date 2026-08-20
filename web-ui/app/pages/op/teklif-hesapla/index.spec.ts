// @vitest-environment nuxt
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CalculatePage from './index.vue'

/**
 * The internal tool's screen (BOYA-22). What is asserted is the part that would break silently: that
 * the form posts numbers rather than the strings it holds, and that the answer's figures reach the
 * screen instead of one of them quietly rendering as NaN.
 *
 * The default form is §5.10's job, because that is the one whose figures are known by heart.
 */
const post = vi.fn()

mockNuxtImport('useApi', () => () => ({ POST: post }))

const ANSWER = {
  priceBookVersion: 'REAL-2026-01',
  netArea: 92,
  areaWasGross: false,
  rooms: [
    { type: 'LIVING_ROOM', label: 'Salon', requiredPhotos: 5 },
    { type: 'KITCHEN', label: 'Mutfak', requiredPhotos: 3 },
  ],
  photoCount: 28,
  lines: [
    { code: 'WALL_PAINT', unit: 'SQM', quantity: 220.83, labourCost: 17114.4, materialCost: 8391.58, lineTotal: 25505.98 },
    { code: 'MOBILIZATION', unit: 'LUMP_SUM', quantity: 1, labourCost: 1900, materialCost: 0, lineTotal: 1900 },
  ],
  totalMinutes: 4116.85,
  billableDays: 3,
  minimumCost: 13500,
  minimumBinding: false,
  totalCost: 50009.39,
  subtotalExVat: 65012.21,
  vatAmount: 13002.44,
  total: 78014.65,
  bandRatio: 0.12,
  bandLow: 68652.89,
  bandHigh: 87376.41,
}

describe('manual quote calculation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    post.mockResolvedValue({ response: { ok: true, status: 200 }, data: ANSWER })
  })

  it('posts the typed job as numbers, not as the strings the form holds', async () => {
    const page = await mountSuspended(CalculatePage)

    await page.find('form').trigger('submit')

    expect(post).toHaveBeenCalledWith('/api/op/price-calculations', {
      body: expect.objectContaining({
        districtCode: 'KADIKOY',
        area: 92,
        areaBasis: 'NET',
        layout: 'THREE_PLUS_ONE',
        scope: 'WHOLE_HOME',
        wallCondition: 'MINOR',
        furnishing: 'FURNISHED',
        doorCount: 8,
        doorColourChange: true,
      }),
    })
  })

  it('leads with the band and the total, and shows the cost behind them', async () => {
    const page = await mountSuspended(CalculatePage)
    await page.find('form').trigger('submit')
    await page.vm.$nextTick()

    const text = page.text()
    expect(text).toContain('68.653')          // band low, whole lira
    expect(text).toContain('87.376')          // band high
    expect(text).toContain('78.015')          // VAT-inclusive total
    expect(text).toContain('50.009')          // internal cost — this is an operator screen
    expect(text).toContain('%30')             // the margin those two imply
    expect(text).not.toContain('NaN')
  })

  it('shows what it assumed: the areas, the net m² and the version that priced it', async () => {
    const page = await mountSuspended(CalculatePage)
    await page.find('form').trigger('submit')
    await page.vm.$nextTick()

    const text = page.text()
    expect(text).toContain('REAL-2026-01')
    expect(text).toContain('Salon')
    expect(text).toContain('Mutfak')
    expect(text).toContain('92,00 m²')
    expect(text).toContain('Duvar boyası')
    expect(text).toContain('220,83 m²')
  })

  it('refuses to ask the API for a job it cannot describe', async () => {
    const page = await mountSuspended(CalculatePage)

    const area = page.find('input[inputmode="decimal"]')
    await area.setValue('yüz')
    await page.find('form').trigger('submit')

    expect(post).not.toHaveBeenCalled()
    expect(page.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('offers only the areas the layout has, with how many of each', async () => {
    const page = await mountSuspended(CalculatePage)

    await page.findAll('.segmented button').find(b => b.text() === 'Seçili alanlar')!.trigger('click')

    const chips = page.findAll('.chips button').map(chip => chip.text())
    // A 3+1 has no study and no balcony, so offering them was offering a click that did nothing.
    expect(chips).toEqual([
      'Salon', 'Ebeveyn yatak odası', 'Yatak odası ×2', 'Mutfak', 'Banyo', 'Koridor',
    ])
  })

  it('counts rooms rather than chips: two taps on a 3+1 can be three areas', async () => {
    const page = await mountSuspended(CalculatePage)
    await page.findAll('.segmented button').find(b => b.text() === 'Seçili alanlar')!.trigger('click')

    const chips = page.findAll('.chips button')
    await chips.find(chip => chip.text() === 'Salon')!.trigger('click')
    await chips.find(chip => chip.text().startsWith('Yatak odası'))!.trigger('click')

    expect(page.text()).toContain('3 alan seçilecek')
  })

  it('drops a selection the new layout cannot hold instead of pricing nothing for it', async () => {
    const page = await mountSuspended(CalculatePage)
    await page.findAll('.segmented button').find(b => b.text() === 'Seçili alanlar')!.trigger('click')
    await page.findAll('.chips button').find(c => c.text().startsWith('Yatak odası'))!.trigger('click')
    expect(page.text()).toContain('2 alan seçilecek')

    // A 1+1 has a master bedroom and no other bedroom: the tick would otherwise sit there pricing
    // nothing, which is exactly how the old checkbox misled.
    await page.findAll('select')[1]!.setValue('ONE_PLUS_ONE')
    await page.find('form').trigger('submit')

    expect(post).not.toHaveBeenCalled()
    expect(page.text()).toContain('0 alan seçilecek')
  })

  it('says so when the answer is that nobody is logged in', async () => {
    post.mockResolvedValue({ response: { ok: false, status: 401 }, data: undefined })
    const page = await mountSuspended(CalculatePage)

    await page.find('form').trigger('submit')
    await page.vm.$nextTick()

    expect(page.text()).toContain('Operatör girişi gerekiyor')
  })
})
