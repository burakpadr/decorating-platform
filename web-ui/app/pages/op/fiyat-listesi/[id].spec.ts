// @vitest-environment nuxt
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PriceBookDetail from './[id].vue'

/**
 * One price list version, in the panel (ADR 0016).
 *
 * The reason this file exists is a duplication the codebase accepts on purpose. The server derives an
 * item's labour cost from its duration at the version's crew rate, and this page computes the same
 * figure again so the operator sees the consequence of a duration *before* saving it. Two copies of an
 * expression is exactly the defect ADR 0016 records — a price book whose two statements about labour
 * disagreed by 3.3x — so the copies have to be held together by something. That is what this is.
 *
 * The figures below are REAL-2026-02's: a 7,500 TL crew day over three people and eight hours, so a
 * person-minute is 5.208333… TL. WALL_PAINT's six minutes are 31.25 TL, and the assertions are worked
 * out from the crew rate rather than read off the fixture, so a wrong derivation cannot agree with them.
 */
const version = vi.fn()
const put = vi.fn()

mockNuxtImport('useApi', () => () => ({
  GET: version,
  PUT: put,
  POST: vi.fn(),
}))

mockNuxtImport('useRoute', () => () => ({
  params: { id: '22222222-2222-7222-8222-222222222222' },
}))

const CREW_DAY = 7500
const CREW_SIZE = 3
const CREW_HOURS = 8

/** The rule under test, stated once here in plain arithmetic: minutes × what a crew minute costs. */
function labourFor(minutes: number): number {
  return minutes * (CREW_DAY / (CREW_SIZE * CREW_HOURS * 60))
}

describe('price list detail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearNuxtData('price-book-22222222-2222-7222-8222-222222222222')
    version.mockResolvedValue({
      response: { ok: true, status: 200 },
      data: {
        id: '22222222-2222-7222-8222-222222222222',
        versionCode: 'REAL-2026-02',
        active: false,
        editable: true,
        createdAt: new Date().toISOString(),
        items: [
          { code: 'WALL_PAINT', unit: 'SQM', labourCost: 31.25, materialCost: 22, labourMinutes: 6 },
          { code: 'MASKING', unit: 'ROOM', labourCost: 130.21, materialCost: 62, labourMinutes: 25 },
        ],
        coefficients: {
          crewSize: CREW_SIZE,
          crewHoursPerDay: CREW_HOURS,
          crewDayCost: CREW_DAY,
          marginRatio: 0.3,
          marginAlertThreshold: 0.2,
          labourVatRate: 0.2,
          materialVatRate: 0.2,
          baseBandRatio: 0.12,
          ceilingHeightM: 2.7,
          grossToNetRatio: 0.82,
          stage1OpeningRatio: 0.12,
        },
      },
    })
    put.mockResolvedValue({ response: { ok: true, status: 200 }, data: {} })
  })

  it('shows each item its duration costs at the crew rate', async () => {
    const page = await mountSuspended(PriceBookDetail)

    const shown = page.findAll('.derived output').map(node => node.text())
    expect(shown).toEqual([
      labourFor(6).toFixed(2).replace('.', ','),
      labourFor(25).toFixed(2).replace('.', ','),
    ])
  })

  it('follows the duration as it is typed, so the cost is seen before it is saved', async () => {
    const page = await mountSuspended(PriceBookDetail)

    // Nine minutes instead of six: half again as long, and the money has to say so unprompted.
    await page.findAll('input.num')[0]!.setValue('9')

    expect(page.findAll('.derived output')[0]!.text())
      .toBe(labourFor(9).toFixed(2).replace('.', ','))
  })

  it('marks the figure as a preview only while the duration differs from what is saved', async () => {
    const page = await mountSuspended(PriceBookDetail)

    expect(page.findAll('.derived output')[0]!.attributes('data-preview')).toBe('false')

    await page.findAll('input.num')[0]!.setValue('9')

    expect(page.findAll('.derived output')[0]!.attributes('data-preview'))
      .toBe('true')
  })

  it('never sends a labour cost: the duration is what it sends', async () => {
    const page = await mountSuspended(PriceBookDetail)

    await page.findAll('input.num')[0]!.setValue('9')
    await page.find('.item-actions .btn').trigger('click')

    expect(put).toHaveBeenCalledTimes(1)
    const body = put.mock.calls[0]![1]!.body
    expect(body).toEqual({ materialCost: 22, labourMinutes: 9 })
    expect(body).not.toHaveProperty('labourCost')
  })

  it('offers no labour input at all, on a draft it is allowed to edit', async () => {
    const page = await mountSuspended(PriceBookDetail)

    // Two rows, two editable figures each — duration and material. A third input per row would mean
    // labour had crept back in as something typed. Scoped to the list: the bulk-increase percent is a
    // .num input as well, and counting it here would make this assertion pass for the wrong reason.
    expect(page.findAll('.items input.num')).toHaveLength(4)
  })
})
