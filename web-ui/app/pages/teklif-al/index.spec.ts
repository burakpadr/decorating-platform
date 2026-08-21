// @vitest-environment nuxt
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QuoteForm from './index.vue'

/**
 * Stage 1's form (BOYA-31, workflow §1.1–1.3).
 *
 * Three assertions carry the ticket. Every step reaches the server, because §8 says people abandon on
 * one device and finish on another and localStorage cannot be trusted with that. The gross/net choice
 * has no default, because a wrong one is an 18% error in every square metre and the customer would
 * never see where it came from. And the furnishing question asks about the painting *day*, not today —
 * most people paint before they move in, and the answer changes the labour by 25%.
 */
const post = vi.fn()
const patch = vi.fn()
const get = vi.fn()
// Hoisted: mockNuxtImport's factory runs before the module body, and this one hands back the mock
// itself rather than a closure over it, so a plain const would not exist yet.
const { navigate } = vi.hoisted(() => ({ navigate: vi.fn() }))

const { route } = vi.hoisted(() => ({ route: vi.fn(() => ({ query: {} as Record<string, string> })) }))

mockNuxtImport('useApi', () => () => ({ POST: post, PATCH: patch, GET: get }))
mockNuxtImport('navigateTo', () => navigate)
mockNuxtImport('useRoute', () => route)

const DRAFT = '01930000-0000-7000-8000-0000000000aa'

/** A draft the customer has already finished once, which is what coming back from the range means. */
const STORED = {
  id: DRAFT,
  status: 'DRAFT',
  priceable: true,
  districtCode: 'USKUDAR',
  area: 140,
  areaBasis: 'GROSS',
  layout: 'FOUR_PLUS_ONE',
  scope: 'WHOLE_HOME',
  furnishing: 'EMPTY',
  doorCount: 6,
  doorColourChange: false,
  wallCondition: 'MAJOR',
}

/**
 * Lets the component's own async work finish.
 *
 * `trigger()` flushes Vue's render queue, not the promise chain behind a click: saving a step is
 * advance → saveCurrentStep → save → two API calls, which is several microtasks deeper than one tick.
 * Without this the assertions read the state as it was mid-save.
 */
async function settle(page: { vm: unknown }) {
  for (let i = 0; i < 6; i += 1) {
    await Promise.resolve()
  }
  await nextTick()
}

/** Fills in the first screen and presses Devam. */
async function completeStepOne(page: any, options: { basis?: boolean } = {}) {
  await page.find('select[name="district"]').setValue('KADIKOY')
  await page.find('input[name="area"]').setValue('92')
  if (options.basis !== false) {
    await page.findAll('[data-group="areaBasis"] button')[1]!.trigger('click')
  }
  await page.find('select[name="layout"]').setValue('THREE_PLUS_ONE')
  await page.findAll('[data-group="scope"] button')[0]!.trigger('click')
  await page.find('.step-actions .btn.primary').trigger('click')
  await settle(page)
}

describe('stage 1 form', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    route.mockReturnValue({ query: {} })
    clearNuxtData(() => true)
    get.mockImplementation((path: string) => Promise.resolve(
      path === '/api/districts'
        ? {
            response: { ok: true, status: 200 },
            data: [{ code: 'KADIKOY', name: 'Kadıköy' }, { code: 'USKUDAR', name: 'Üsküdar' }],
          }
        : { response: { ok: true, status: 200 }, data: STORED },
    ))
    post.mockResolvedValue({ response: { ok: true, status: 201 }, data: { id: DRAFT } })
    patch.mockResolvedValue({ response: { ok: true, status: 200 }, data: { id: DRAFT } })
  })

  // ===========================================================================================
  // Coming back to change an answer
  // ===========================================================================================

  it('fills the form from the draft rather than starting blank', async () => {
    route.mockReturnValue({ query: { talep: DRAFT } })
    const page = await mountSuspended(QuoteForm)

    // The complaint this fixes: "Cevapları değiştir" wiped everything. The answers are on the server —
    // that is the whole point of PATCHing every step — so a form that ignores them is throwing away
    // work the customer already did.
    expect((page.find('select[name="district"]').element as HTMLSelectElement).value)
      .toBe('USKUDAR')
    expect((page.find('input[name="area"]').element as HTMLInputElement).value).toBe('140')
    expect(page.findAll('[data-group="areaBasis"] button')[0]!.attributes('aria-pressed'))
      .toBe('true')
    expect((page.find('select[name="layout"]').element as HTMLSelectElement).value)
      .toBe('FOUR_PLUS_ONE')
  })

  it('does not create a second draft for a draft it was given', async () => {
    route.mockReturnValue({ query: { talep: DRAFT } })
    const page = await mountSuspended(QuoteForm)

    await page.find('.step-actions .btn.primary').trigger('click')
    await settle(page)

    expect(post).not.toHaveBeenCalled()
    expect(patch.mock.calls[0]![1]!.params.path.id).toBe(DRAFT)
  })

  it('lets a finished draft be edited from any step, not only forwards', async () => {
    route.mockReturnValue({ query: { talep: DRAFT } })
    const page = await mountSuspended(QuoteForm)

    const tabs = page.findAll('.steps button')
    expect(tabs).toHaveLength(3)
    expect(tabs.map(tab => tab.attributes('disabled'))).toEqual([undefined, undefined, undefined])

    await tabs[2]!.trigger('click')
    await settle(page)

    expect(page.text()).toContain('Duvar durumu')
    expect(page.findAll('[data-group="wallCondition"] button')[2]!.attributes('aria-pressed'))
      .toBe('true')
  })

  it('a fresh form cannot jump to a step it has not earned', async () => {
    const page = await mountSuspended(QuoteForm)

    const tabs = page.findAll('.steps button')
    expect(tabs[1]!.attributes('disabled')).toBeDefined()

    await tabs[1]!.trigger('click')

    expect(page.text()).toContain('Ev bilgileri')
  })

  it('saves the step being left, so a jump does not lose the edit', async () => {
    route.mockReturnValue({ query: { talep: DRAFT } })
    const page = await mountSuspended(QuoteForm)
    await page.find('input[name="area"]').setValue('150')

    await page.findAll('.steps button')[1]!.trigger('click')
    await settle(page)

    expect(patch).toHaveBeenCalledTimes(1)
    expect(patch.mock.calls[0]![1]!.body).toMatchObject({ area: 150 })
  })

  it('going back with an unfinished step keeps what was typed instead of refusing', async () => {
    const page = await mountSuspended(QuoteForm)
    await completeStepOne(page)
    // Nothing answered on step 2 yet.

    await page.find('.step-actions .btn:not(.primary)').trigger('click')
    await settle(page)

    // Backwards is always allowed: the form holds every answer in one object, so nothing is lost and
    // there is nothing to validate. Only going forward has to be earned.
    expect(page.text()).toContain('Ev bilgileri')
    expect((page.find('input[name="area"]').element as HTMLInputElement).value).toBe('92')
  })

  it('offers the range directly once every answer is in', async () => {
    route.mockReturnValue({ query: { talep: DRAFT } })
    const page = await mountSuspended(QuoteForm)

    // Somebody who came back to change one answer should not have to press Devam three times to see
    // what it did to the price.
    await page.find('.to-result').trigger('click')
    await settle(page)

    expect(navigate).toHaveBeenCalledWith(`/teklif-al/sonuc?talep=${DRAFT}`)
  })

  it('a draft it cannot read is said out loud, not started over silently', async () => {
    // Its own id: useAsyncData keys by it, and sharing a key with a test that succeeded means reading
    // that test's payload instead of running this one's handler.
    const unreadable = '01930000-0000-7000-8000-0000000000ff'
    route.mockReturnValue({ query: { talep: unreadable } })
    get.mockImplementation((path: string) => Promise.resolve(
      path === '/api/districts'
        ? { response: { ok: true, status: 200 }, data: [{ code: 'KADIKOY', name: 'Kadıköy' }] }
        : { response: { ok: false, status: 403 }, error: {} },
    ))
    const page = await mountSuspended(QuoteForm)

    // The reported bug from the other side: a blank form under a draft id means the next Devam creates a
    // *second* draft, and the answers stay on the first where nothing will read them again.
    expect(page.text()).toContain('bu cihazda açılamıyor')
    expect(page.find('select[name="district"]').exists()).toBe(false)
  })

  it('offers only the districts the API serves', async () => {
    const page = await mountSuspended(QuoteForm)

    const options = page.findAll('select[name="district"] option')
      .map(option => option.attributes('value'))
    // The empty first option is the placeholder: nothing is preselected, here either.
    expect(options).toEqual(['', 'KADIKOY', 'USKUDAR'])
    expect(get).toHaveBeenCalledWith('/api/districts')
  })

  it('acceptance: the gross/net choice has no default and blocks the step until it is made', async () => {
    const page = await mountSuspended(QuoteForm)

    // Neither is chosen when the screen opens. A default here is an 18% error in every square metre
    // that the customer would never see the source of.
    const basis = page.findAll('[data-group="areaBasis"] button')
    expect(basis.map(b => b.attributes('aria-pressed'))).toEqual(['false', 'false'])

    await completeStepOne(page, { basis: false })

    expect(post).not.toHaveBeenCalled()
    expect(page.text()).toContain('Bu soru cevaplanmalı')
  })

  it('acceptance: finishing a step writes it to the server', async () => {
    const page = await mountSuspended(QuoteForm)

    await completeStepOne(page)

    expect(post).toHaveBeenCalledWith('/api/quote-requests')
    expect(patch).toHaveBeenCalledTimes(1)
    expect(patch.mock.calls[0]![1]!.body).toEqual({
      districtCode: 'KADIKOY',
      area: 92,
      areaBasis: 'NET',
      layout: 'THREE_PLUS_ONE',
      scope: 'WHOLE_HOME',
      selectedRooms: undefined,
    })
  })

  it('creates the draft once, then patches the same one', async () => {
    const page = await mountSuspended(QuoteForm)

    await completeStepOne(page)
    await page.findAll('[data-group="furnishing"] button')[2]!.trigger('click')
    await page.findAll('[data-group="doors"] button')[1]!.trigger('click')
    await page.find('.step-actions .btn.primary').trigger('click')
    await settle(page)

    expect(post).toHaveBeenCalledTimes(1)
    expect(patch).toHaveBeenCalledTimes(2)
    expect(patch.mock.calls[1]![1]!.params.path.id).toBe(DRAFT)
  })

  it('acceptance: the furnishing question is about the painting day, not today', async () => {
    const page = await mountSuspended(QuoteForm)
    await completeStepOne(page)

    // §1.2: "eşyalı mı" değil, "boya *günü* eşya olacak mı". Customers paint before they move in, and
    // the answer moves the labour by 25% — so the question that gets asked has to be the one that
    // matters.
    expect(page.text()).toContain('Boya günü evde eşya olacak mı?')
    expect(page.text()).not.toContain('Eviniz eşyalı mı')
  })

  it('asks how many doors only once painting them is chosen', async () => {
    const page = await mountSuspended(QuoteForm)
    await completeStepOne(page)

    expect(page.find('input[name="doorCount"]').exists()).toBe(false)

    await page.findAll('[data-group="doors"] button')[0]!.trigger('click')

    expect(page.find('input[name="doorCount"]').exists()).toBe(true)
  })

  it('sends zero doors when the customer says no, rather than leaving it unanswered', async () => {
    const page = await mountSuspended(QuoteForm)
    await completeStepOne(page)

    await page.findAll('[data-group="furnishing"] button')[0]!.trigger('click')
    await page.findAll('[data-group="doors"] button')[1]!.trigger('click')
    await page.find('.step-actions .btn.primary').trigger('click')
    await settle(page)

    expect(patch.mock.calls[1]![1]!.body).toEqual({
      furnishing: 'EMPTY',
      doorCount: 0,
      doorColourChange: false,
    })
  })

  it('requires at least one area when the scope is a selection', async () => {
    const page = await mountSuspended(QuoteForm)
    await page.find('select[name="district"]').setValue('KADIKOY')
    await page.find('input[name="area"]').setValue('92')
    await page.findAll('[data-group="areaBasis"] button')[1]!.trigger('click')
    await page.find('select[name="layout"]').setValue('THREE_PLUS_ONE')

    await page.findAll('[data-group="scope"] button')[1]!.trigger('click')
    await page.find('.step-actions .btn.primary').trigger('click')

    expect(post).not.toHaveBeenCalled()
    expect(page.text()).toContain('En az bir alan seçin')
  })

  it('refuses an area nobody has: 20 to 500 m²', async () => {
    const page = await mountSuspended(QuoteForm)
    await page.find('select[name="district"]').setValue('KADIKOY')
    await page.findAll('[data-group="areaBasis"] button')[1]!.trigger('click')
    await page.find('select[name="layout"]').setValue('THREE_PLUS_ONE')
    await page.findAll('[data-group="scope"] button')[0]!.trigger('click')

    await page.find('input[name="area"]').setValue('4')
    await page.find('.step-actions .btn.primary').trigger('click')

    expect(post).not.toHaveBeenCalled()
    expect(page.text()).toContain('20 ile 500')
  })

  it('the last step patches the wall condition and goes to the range', async () => {
    const page = await mountSuspended(QuoteForm)
    await completeStepOne(page)
    await page.findAll('[data-group="furnishing"] button')[2]!.trigger('click')
    await page.findAll('[data-group="doors"] button')[1]!.trigger('click')
    await page.find('.step-actions .btn.primary').trigger('click')
    await settle(page)

    await page.findAll('[data-group="wallCondition"] button')[3]!.trigger('click')
    await page.find('.step-actions .btn.primary').trigger('click')
    await settle(page)

    expect(patch.mock.calls[2]![1]!.body).toEqual({ wallCondition: 'UNSURE' })
    expect(navigate).toHaveBeenCalledWith(`/teklif-al/sonuc?talep=${DRAFT}`)
  })

  it('a step that fails to save does not advance', async () => {
    patch.mockResolvedValue({ response: { ok: false, status: 500 }, error: {} })
    const page = await mountSuspended(QuoteForm)

    await completeStepOne(page)

    // Advancing on a failed write is how an answer goes missing without anybody seeing it — the
    // customer finishes the form and the draft is short one screen.
    expect(page.text()).toContain('Kaydedilemedi')
    expect(page.text()).toContain('Ev bilgileri')
  })

  it('a request that throws leaves the button usable, not stuck', async () => {
    // A refused CORS preflight rejects the promise rather than answering, and the button used to read
    // "Kaydediliyor…" for ever with nothing said. A stuck button is worse than an error: the customer
    // waits instead of retrying. This is how the missing PATCH in the CORS method list showed up.
    patch.mockRejectedValue(new TypeError('Failed to fetch'))
    const page = await mountSuspended(QuoteForm)

    await completeStepOne(page)

    expect(page.text()).toContain('Kaydedilemedi')
    expect(page.find('.step-actions .btn.primary').text()).toBe('Devam')
    expect(page.find('.step-actions .btn.primary').attributes('disabled')).toBeUndefined()
  })

  it('a district we do not serve is named, not reported as a generic failure', async () => {
    patch.mockResolvedValue({
      response: { ok: false, status: 422 },
      error: { type: 'urn:decorating:district-not-served', districtCode: 'KADIKOY' },
    })
    const page = await mountSuspended(QuoteForm)

    await completeStepOne(page)

    expect(page.text()).toContain('Kadıköy ilçesinde henüz hizmet vermiyoruz')
  })
})
