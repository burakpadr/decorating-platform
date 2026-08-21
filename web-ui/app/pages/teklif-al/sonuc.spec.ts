// @vitest-environment nuxt
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Result from './sonuc.vue'

/**
 * Stage 1's result screen (BOYA-32, workflow §1.5).
 *
 * §1.5 calls this the biggest loss point in the process: the customer who sees a range and leaves has
 * left no phone number and cannot be reached again. So the assertions are about the two things that
 * decide whether they stay — that the range is shown as a range and not apologised for, and that the
 * screen says *why* it is wide in terms of what they answered. A generic "tahmini fiyat" reads as a
 * business that does not know its prices.
 */
const get = vi.fn()
const post = vi.fn()

mockNuxtImport('useApi', () => () => ({ GET: get, POST: post }))
mockNuxtImport('useRoute', () => () => ({ query: { talep: 'draft-1' } }))

const ANSWERS = {
  id: 'draft-1',
  status: 'DRAFT',
  priceable: true,
  districtCode: 'KADIKOY',
  area: 92,
  areaBasis: 'NET',
  layout: 'THREE_PLUS_ONE',
  scope: 'WHOLE_HOME',
  furnishing: 'FURNISHED',
  doorCount: 8,
  doorColourChange: true,
  wallCondition: 'MINOR',
}

const ESTIMATE = {
  low: 45241.33,
  high: 57579.88,
  bandRatio: 0.12,
  netArea: 92,
  areaWasGross: false,
  rooms: [
    { type: 'LIVING_ROOM', label: 'Salon', requiredPhotos: 5 },
    { type: 'KITCHEN', label: 'Mutfak', requiredPhotos: 3 },
  ],
  photoCount: 28,
}

describe('stage 1 result', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearNuxtData(() => true)
    get.mockResolvedValue({ response: { ok: true, status: 200 }, data: ANSWERS })
    post.mockResolvedValue({ response: { ok: true, status: 200 }, data: ESTIMATE })
  })

  it('leads with the range, both ends of it', async () => {
    const page = await mountSuspended(Result)

    const range = page.find('.range').text()
    expect(range).toContain('45.241')
    expect(range).toContain('57.580')
  })

  it('reads the draft and asks for the estimate, in that order', async () => {
    await mountSuspended(Result)

    expect(get).toHaveBeenCalledWith('/api/quote-requests/{id}',
      { params: { path: { id: 'draft-1' } } })
    expect(post).toHaveBeenCalledWith('/api/quote-requests/{id}/estimate',
      { params: { path: { id: 'draft-1' } } })
  })

  it('§1: shows no cost and no margin', async () => {
    const page = await mountSuspended(Result)

    const text = page.text()
    // The customer's screen carries a price. What the job costs the business is the operator's screen,
    // and this response never contained it — asserted so that a later "just for debugging" cannot.
    expect(text).not.toContain('Maliyet')
    expect(text).not.toContain('Marj')
    expect(page.html()).not.toContain('totalCost')
  })

  it('names the reason the band is wide, not just its width', async () => {
    get.mockResolvedValue({
      response: { ok: true, status: 200 },
      data: { ...ANSWERS, wallCondition: 'UNSURE' },
    })
    post.mockResolvedValue({
      response: { ok: true, status: 200 },
      data: { ...ESTIMATE, bandRatio: 0.27 },
    })
    const page = await mountSuspended(Result)

    // §1.5 requires the sentence, and requires it to be about what they said: "duvar durumunu
    // bilmediğimiz için". A percentage on its own explains nothing to the person reading it.
    expect(page.text()).toContain('emin olmadığınızı belirttiniz')
    expect(page.text()).toContain('%27')
  })

  it('says a gross area was converted, because that is also why the band widened', async () => {
    get.mockResolvedValue({
      response: { ok: true, status: 200 },
      data: { ...ANSWERS, areaBasis: 'GROSS', area: 112 },
    })
    post.mockResolvedValue({
      response: { ok: true, status: 200 },
      data: { ...ESTIMATE, areaWasGross: true, netArea: 91.84, bandRatio: 0.17 },
    })
    const page = await mountSuspended(Result)

    expect(page.text()).toContain('Brüt alan verdiniz')
    expect(page.text()).toContain('91,84')
  })

  it('does not invent a reason that does not apply', async () => {
    const page = await mountSuspended(Result)

    // Wall condition MINOR and a net area: the only widening is the base band, so neither of the two
    // specific sentences belongs on the screen.
    expect(page.text()).not.toContain('emin olmadığınızı belirttiniz')
    expect(page.text()).not.toContain('Brüt alan verdiniz')
  })

  it('offers changing an answer as an action, not as small print', async () => {
    const page = await mountSuspended(Result)

    // Showing somebody a summary and then making the way to correct it the faintest thing on the screen
    // is an invitation nobody accepts. It belongs in the row where "what can I do now" is answered, and
    // it carries the draft — a blank form was the original bug.
    const edit = page.find('.actions .edit')
    expect(edit.exists()).toBe(true)
    expect(edit.attributes('href')).toBe('/teklif-al?talep=draft-1')
    expect(edit.classes()).toContain('outline')
  })

  it('summarises what was answered, so the number can be argued with', async () => {
    const page = await mountSuspended(Result)

    const summary = page.find('.summary').text()
    expect(summary).toContain('Kadıköy')
    expect(summary).toContain('3+1')
    expect(summary).toContain('Eşyalı')
    expect(summary).toContain('8 kapı')
    expect(summary).toContain('Ufak çatlak')
  })

  it("offers §1.5's three options: continue, take it by SMS, leave", async () => {
    const page = await mountSuspended(Result)

    expect(page.find('.actions .btn.primary').attributes('href')).toBe('/cekim')
    expect(page.find('.sms-offer').text()).toContain('SMS ile gönder')
    // Leaving is not one of the decisions: it is the corner link, present whatever the page is showing.
    expect(page.find('.back-home').attributes('href')).toBe('/')
  })

  it('asks for the number only once the SMS option is chosen', async () => {
    const page = await mountSuspended(Result)

    expect(page.find('input[name="phone"]').exists()).toBe(false)

    await page.find('.sms-offer').trigger('click')

    expect(page.find('input[name="phone"]').exists()).toBe(true)
  })

  it('sends the number and says it was kept', async () => {
    const page = await mountSuspended(Result)
    await page.find('.sms-offer').trigger('click')
    await page.find('input[name="phone"]').setValue('0555 123 45 67')

    await page.find('.sms-form .btn').trigger('click')

    expect(post).toHaveBeenCalledWith('/api/quote-requests/{id}/estimate-sms', {
      params: { path: { id: 'draft-1' } },
      body: { phone: '0555 123 45 67' },
    })
    expect(page.text()).toContain('Numaranız kaydedildi')
    // The field goes away with the offer: leaving it there invites a second send nobody meant.
    expect(page.find('input[name="phone"]').exists()).toBe(false)
  })

  it('refuses a number no SMS can reach, before asking the server', async () => {
    const page = await mountSuspended(Result)
    await page.find('.sms-offer').trigger('click')
    await page.find('input[name="phone"]').setValue('0212 123 45 67')

    await page.find('.sms-form .btn').trigger('click')

    // The same rule the server applies (PhoneNumber). Checked here as well so the customer is told
    // immediately rather than after a round trip — and the server still decides.
    expect(page.text()).toContain('Cep telefonu numarası girin')
    expect(post).toHaveBeenCalledTimes(1)   // the estimate itself, and nothing else
  })

  it('says so when the send fails, and keeps the number on screen to retry', async () => {
    const page = await mountSuspended(Result)
    await page.find('.sms-offer').trigger('click')
    await page.find('input[name="phone"]').setValue('05551234567')
    post.mockResolvedValue({ response: { ok: false, status: 500 }, error: {} })

    await page.find('.sms-form .btn').trigger('click')

    expect(page.text()).toContain('Gönderilemedi')
    expect(page.find('input[name="phone"]').exists()).toBe(true)
  })

  it('sends an unfinished draft back to the form rather than showing a number', async () => {
    get.mockResolvedValue({
      response: { ok: true, status: 200 },
      data: { ...ANSWERS, priceable: false, wallCondition: null },
    })
    const page = await mountSuspended(Result)

    expect(post).not.toHaveBeenCalled()
    expect(page.text()).toContain('cevaplanmamış')
    expect(page.find('a[href="/teklif-al"]').exists()).toBe(true)
  })

  it('says so when the draft itself cannot be read, rather than waiting for ever', async () => {
    // The failure mode that looks most like a working page: a spinner with nothing behind it. This is
    // how the missing GET endpoint presented — 405 from the API, "Hesaplanıyor…" on screen.
    get.mockResolvedValue({ response: { ok: false, status: 405 }, error: {} })
    const page = await mountSuspended(Result)

    expect(page.text()).toContain('hesaplanamadı')
    expect(page.text()).not.toContain('Hesaplanıyor')
  })

  it('says so when the estimate cannot be computed, rather than showing an empty range', async () => {
    post.mockResolvedValue({ response: { ok: false, status: 500 }, error: {} })
    const page = await mountSuspended(Result)

    expect(page.text()).toContain('hesaplanamadı')
    expect(page.find('.range').exists()).toBe(false)
    // Whoever is looking at a failure needs the way out more than whoever is looking at a price.
    expect(page.find('.back-home').exists()).toBe(true)
  })
})
