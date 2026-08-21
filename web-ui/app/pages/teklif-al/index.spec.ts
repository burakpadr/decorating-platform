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

mockNuxtImport('useApi', () => () => ({ POST: post, PATCH: patch, GET: get }))
mockNuxtImport('navigateTo', () => navigate)

const DRAFT = '01930000-0000-7000-8000-0000000000aa'

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
}

describe('stage 1 form', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    get.mockResolvedValue({
      response: { ok: true, status: 200 },
      data: [
        { code: 'KADIKOY', name: 'Kadıköy' },
        { code: 'USKUDAR', name: 'Üsküdar' },
      ],
    })
    post.mockResolvedValue({ response: { ok: true, status: 201 }, data: { id: DRAFT } })
    patch.mockResolvedValue({ response: { ok: true, status: 200 }, data: { id: DRAFT } })
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

    await page.findAll('[data-group="wallCondition"] button')[3]!.trigger('click')
    await page.find('.step-actions .btn.primary').trigger('click')

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
