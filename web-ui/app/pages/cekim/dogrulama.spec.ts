// @vitest-environment nuxt
/**
 * Stage 3's verification screen (§3.1, §11, BOYA-45).
 *
 * The promises being tested are the customer's. That a bad number is caught before an SMS is paid
 * for; that the three refusals §11 defines lead to three different next moves — try again, ask for a
 * new code, wait — because a single "wrong code" leaves somebody retyping digits at a dead one; and
 * that verifying is not the end, since §3's arrow into ANALYZING needs the photographs too.
 */
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Dogrulama from './dogrulama.vue'

const post = vi.fn()
const { navigate } = vi.hoisted(() => ({ navigate: vi.fn() }))

mockNuxtImport('useApi', () => () => ({ POST: post }))
mockNuxtImport('navigateTo', () => navigate)
mockNuxtImport('useRoute', () => () => ({ query: { talep: 'draft-1' } }))

const ok = {
  response: { ok: true, status: 202 },
  data: { expiresAt: new Date(Date.now() + 300_000).toISOString() },
}
const submitted = {
  response: { ok: true, status: 200 },
  data: { status: 'ANALYZING', respondBy: '2026-09-02T08:00:00Z' },
}

async function type(page: Awaited<ReturnType<typeof mountSuspended>>, selector: string, value: string) {
  await page.find(selector).setValue(value)
}

async function settle(page: Awaited<ReturnType<typeof mountSuspended>>) {
  await new Promise(resolve => setTimeout(resolve, 0))
  await page.vm.$nextTick()
}

/** The same, for the test that freezes time outright: a real setTimeout would never fire. */
async function settleFrozen(page: Awaited<ReturnType<typeof mountSuspended>>) {
  await vi.advanceTimersByTimeAsync(0)
  await page.vm.$nextTick()
}

describe('telefon doğrulama', () => {
  beforeEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
    clearNuxtData(() => true)
    post.mockImplementation(async (path: string) =>
      (path === '/api/quote-requests/{id}/submit' ? submitted : ok))
  })

  it('refuses a number that is not a Turkish mobile before paying for an SMS', async () => {
    const page = await mountSuspended(Dogrulama)

    await type(page, '.phone', '0212 555 44 33')
    await page.find('.send').trigger('click')
    await settle(page)

    expect(post).not.toHaveBeenCalled()
    expect(page.text()).toContain('cep telefonu')
  })

  it('sends the code and asks for it', async () => {
    const page = await mountSuspended(Dogrulama)

    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)

    expect(post).toHaveBeenCalledWith('/api/otp/send', { body: { phone: '0532 111 22 33' } })
    expect(page.find('.code').exists()).toBe(true)
  })

  it('§11: counts the wait down rather than stating a minute that stops being true', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    post.mockResolvedValue({
      response: { ok: false, status: 429 }, data: null, error: { retryAfterSeconds: 60 },
    })
    const page = await mountSuspended(Dogrulama)

    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)

    expect(page.text()).toContain('Çok sık')
    // A full minute reads as a clock; it drops to plain seconds once there is less than one.
    expect(page.text()).toContain('1:00')
    expect(page.find('.send').attributes('disabled')).toBeDefined()

    vi.advanceTimersByTime(40_000)
    await settle(page)
    expect(page.text()).toContain('20 saniye')

    vi.advanceTimersByTime(21_000)
    await settle(page)
    expect(page.text()).toContain('Şimdi tekrar')
    expect(page.find('.send').attributes('disabled')).toBeUndefined()
    vi.useRealTimers()
  })

  it('takes the wait from the server: §11\'s daily limit is not "1 dakika"', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    post.mockResolvedValue({
      response: { ok: false, status: 429 }, data: null, error: { retryAfterSeconds: 86_400 },
    })
    const page = await mountSuspended(Dogrulama)

    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)

    // A ticking mm:ss clock over a day would be useless, and "1 dakika" would be false.
    expect(page.text()).toContain('24 saat')
    vi.useRealTimers()
  })

  it('does not send again while the wait is running', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    post.mockResolvedValue({
      response: { ok: false, status: 429 }, data: null, error: { retryAfterSeconds: 60 },
    })
    const page = await mountSuspended(Dogrulama)
    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)
    post.mockClear()

    await page.find('.send').trigger('click')
    await settle(page)

    expect(post).not.toHaveBeenCalled()
    vi.useRealTimers()
  })

  it('verifies the code and hands the request over, then goes to the waiting screen', async () => {
    const page = await mountSuspended(Dogrulama)
    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)

    await type(page, '.code', '123456')
    await page.find('.confirm').trigger('click')
    await settle(page)

    expect(post).toHaveBeenCalledWith('/api/otp/verify', { body: { code: '123456' } })
    expect(post).toHaveBeenCalledWith('/api/quote-requests/{id}/submit', {
      params: { path: { id: 'draft-1' } },
    })
    expect(navigate).toHaveBeenCalledWith(
      '/cekim/bekleme?talep=draft-1&yanit=2026-09-02T08%3A00%3A00Z')
  })

  it('shows how long the code has left, and counts it down', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    post.mockResolvedValue({
      response: { ok: true, status: 202 },
      data: { expiresAt: new Date(Date.now() + 300_000).toISOString() },
    })
    const page = await mountSuspended(Dogrulama)

    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)

    expect(page.text()).toContain('5:00')
    expect(page.find('.dial').exists()).toBe(true)

    vi.advanceTimersByTime(60_000)
    await settle(page)
    expect(page.text()).toContain('4:00')
    vi.useRealTimers()
  })

  it('says the code expired rather than letting it be typed and refused', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    post.mockResolvedValue({
      response: { ok: true, status: 202 },
      data: { expiresAt: new Date(Date.now() + 300_000).toISOString() },
    })
    const page = await mountSuspended(Dogrulama)
    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)

    vi.advanceTimersByTime(301_000)
    await settle(page)

    // Expired and mistyped used to arrive as the same sentence, which is how somebody retypes the
    // right digits and is told again that they are wrong.
    expect(page.text()).toContain('süresi doldu')
    expect(page.find('.confirm').attributes('disabled')).toBeDefined()
    // The dial closes into a full ring at zero. An empty one reads as a dial that never started, and
    // it left the expired colour with nothing to colour.
    expect(page.find('.life').attributes('data-expired')).toBe('true')
    expect(page.find('.sweep').attributes('stroke-dashoffset')).toBe('0')
    vi.useRealTimers()
  })

  it('does not spend a round trip on a code its own clock says is gone', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    post.mockResolvedValue({
      response: { ok: true, status: 202 },
      data: { expiresAt: new Date(Date.now() + 300_000).toISOString() },
    })
    const page = await mountSuspended(Dogrulama)
    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)
    vi.advanceTimersByTime(301_000)
    await settle(page)
    post.mockClear()

    await type(page, '.code', '123456')
    await page.find('.confirm').trigger('click')
    await settle(page)

    expect(post).not.toHaveBeenCalled()
    vi.useRealTimers()
  })

  it('will not send another while the one in hand still works', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    post.mockResolvedValue({
      response: { ok: true, status: 202 },
      data: { expiresAt: new Date(Date.now() + 60_000).toISOString() },
    })
    const page = await mountSuspended(Dogrulama)
    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)
    post.mockClear()

    // Asking again would invalidate the code that is probably arriving right now.
    expect(page.find('.resend').attributes('disabled')).toBeDefined()
    await page.find('.resend').trigger('click')
    await settle(page)
    expect(post).not.toHaveBeenCalled()

    vi.advanceTimersByTime(61_000)
    await settle(page)

    expect(page.find('.resend').attributes('disabled')).toBeUndefined()
    vi.useRealTimers()
  })

  it('offers a new code the moment the old one dies, with no second wait', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    post.mockResolvedValue({
      response: { ok: true, status: 202 },
      data: { expiresAt: new Date(Date.now() + 60_000).toISOString() },
    })
    const page = await mountSuspended(Dogrulama)
    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)
    vi.advanceTimersByTime(61_000)
    await settle(page)
    post.mockClear()

    await page.find('.resend').trigger('click')
    await settle(page)

    // The code's life and §11's send window are the same minute on purpose, so expiry and eligibility
    // arrive together and the customer is never left looking at two clocks.
    expect(post).toHaveBeenCalledWith('/api/otp/send', { body: { phone: '0532 111 22 33' } })
    vi.useRealTimers()
  })

  it('re-reads the clock when the page comes back, because timers stop in a hidden tab', async () => {
    // The scenario this whole clock exists for: the customer switches to their messages to fetch the
    // code. Browsers throttle timers in a hidden tab, so the interval stops — and returning to a
    // countdown frozen where they left it would show a dead code as alive. Found in a real browser,
    // where the clock sat at 0:12 for as long as the tab was in the background.
    const start = Date.now()
    vi.useFakeTimers({ now: start })
    post.mockResolvedValue({
      response: { ok: true, status: 202 },
      data: { expiresAt: new Date(start + 60_000).toISOString() },
    })
    const page = await mountSuspended(Dogrulama)
    await page.find('.phone').setValue('0532 111 22 33')
    await page.find('.send').trigger('click')
    await settleFrozen(page)
    expect(page.text()).toContain('1:00')

    // Time passes with no timer callbacks at all, which is what throttling looks like.
    vi.setSystemTime(start + 61_000)
    await settleFrozen(page)
    expect(page.text()).toContain('1:00')

    document.dispatchEvent(new Event('visibilitychange'))
    await settleFrozen(page)

    expect(page.text()).toContain('süresi doldu')
    expect(page.find('.resend').attributes('disabled')).toBeUndefined()
    vi.useRealTimers()
  })

  it('a locked code asks for a new one instead of another try (§11)', async () => {
    const page = await mountSuspended(Dogrulama)
    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)

    post.mockResolvedValue({ response: { ok: false, status: 423 }, data: null })
    await type(page, '.code', '000000')
    await page.find('.confirm').trigger('click')
    await settle(page)

    expect(page.text()).toContain('Yeni bir kod isteyin')
    expect(navigate).not.toHaveBeenCalled()
    expect(page.find('.resend').exists()).toBe(true)
  })

  it('a wrong code is a different message from a locked one', async () => {
    const page = await mountSuspended(Dogrulama)
    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)

    post.mockResolvedValue({ response: { ok: false, status: 422 }, data: null })
    await type(page, '.code', '000000')
    await page.find('.confirm').trigger('click')
    await settle(page)

    expect(page.text()).toContain('doğrulanamadı')
    expect(page.text()).not.toContain('Yeni bir kod isteyin')
  })

  it('sends the customer back to finish the photographs when frames are missing', async () => {
    const page = await mountSuspended(Dogrulama)
    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)

    post.mockImplementation(async (path: string) => (path === '/api/otp/verify'
      ? { response: { ok: true, status: 204 }, data: null }
      : { response: { ok: false, status: 409 }, data: null }))
    await type(page, '.code', '123456')
    await page.find('.confirm').trigger('click')
    await settle(page)

    expect(page.text()).toContain('eksik fotoğrafları')
    expect(navigate).not.toHaveBeenCalled()
  })

  it('will not send a code that is not six digits', async () => {
    const page = await mountSuspended(Dogrulama)
    await type(page, '.phone', '0532 111 22 33')
    await page.find('.send').trigger('click')
    await settle(page)
    post.mockClear()

    await type(page, '.code', '123')
    await page.find('.confirm').trigger('click')
    await settle(page)

    expect(post).not.toHaveBeenCalled()
  })
})
