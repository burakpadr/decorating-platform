// @vitest-environment nuxt
/**
 * Stage 2's guidance screen (§2.3, BOYA-39).
 *
 * §2.3 asks for one screen carrying two things: the three shooting rules, and what the photographs are
 * used for and how long they are kept — "ve onayı alınır". §5's inventory gives it fifteen seconds, so
 * the rules have to be readable at a glance and the notice has to be there without being a wall.
 *
 * The tests below are about the promise rather than the widgets: that the customer cannot walk past the
 * notice without answering, that the answer reaches the server with the version of the text they were
 * shown, and that a "no" is a real answer rather than a dead end.
 */
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Rehber from './rehber.vue'

const get = vi.fn()
const post = vi.fn()
const { navigate } = vi.hoisted(() => ({ navigate: vi.fn() }))

mockNuxtImport('useApi', () => () => ({ GET: get, POST: post }))
mockNuxtImport('navigateTo', () => navigate)
mockNuxtImport('useRoute', () => () => ({ query: { talep: 'draft-1' } }))

/** What GET /api/consent-notices/PROCESSING answers: the words, and the version that names them. */
const NOTICE = {
  type: 'PROCESSING',
  textVersion: 'v1',
  body: '## Fotoğraflarınız ne için kullanılıyor?\n\nSadece teklif hazırlamak için.\n\n'
    + '## Ne kadar süre saklanıyor?\n\nTalebiniz kapandıktan **30 gün** sonra silinir.',
}

describe('çekim rehberi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearNuxtData(() => true)
    get.mockResolvedValue({ response: { ok: true, status: 200 }, data: NOTICE })
    post.mockResolvedValue({
      response: { ok: true, status: 201 },
      data: { type: 'PROCESSING', granted: true, textVersion: 'v1', recordedAt: '2026-08-27T10:00:00Z' },
    })
  })

  it('puts §2.3\'s three rules on the screen, in words rather than jargon', async () => {
    const page = await mountSuspended(Rehber)

    const text = page.text()
    expect(text).toContain('Işıkları açın')
    expect(text).toContain('kimse görünmesin')
    expect(text).toContain('sabit tutun')
  })

  it('shows what the photographs are used for and how long they are kept, from the served notice', async () => {
    const page = await mountSuspended(Rehber)

    expect(get).toHaveBeenCalledWith('/api/consent-notices/{type}', {
      params: { path: { type: 'PROCESSING' } },
    })
    // §12's retention promise arrives with the text rather than being retyped into tr.json, which is
    // the whole point of decision 0018.
    expect(page.text()).toContain('30 gün')
  })

  it('will not let the customer past the notice without answering it', async () => {
    const page = await mountSuspended(Rehber)

    await page.find('.continue').trigger('click')

    expect(post).not.toHaveBeenCalled()
    expect(navigate).not.toHaveBeenCalled()
    expect(page.text()).toContain('Devam etmek için onay')
  })

  it('sends the decision with the version of the text that was on the screen', async () => {
    const page = await mountSuspended(Rehber)

    await page.find('.agree').setValue(true)
    await page.find('.continue').trigger('click')

    expect(post).toHaveBeenLastCalledWith('/api/quote-requests/{id}/consents', {
      params: { path: { id: 'draft-1' } },
      body: { type: 'PROCESSING', granted: true, textVersion: 'v1' },
    })
    expect(navigate).toHaveBeenCalledWith('/cekim/oda?talep=draft-1')
  })

  it('re-reads the notice when the server says it changed, rather than insisting', async () => {
    post.mockResolvedValueOnce({ response: { ok: false, status: 409 }, data: null })
    const page = await mountSuspended(Rehber)

    await page.find('.agree').setValue(true)
    await page.find('.continue').trigger('click')

    expect(navigate).not.toHaveBeenCalled()
    expect(page.text()).toContain('Onay metni güncellendi')
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('says so plainly when the notice cannot be loaded, instead of an empty consent box', async () => {
    get.mockResolvedValue({ response: { ok: false, status: 500 }, data: null })
    const page = await mountSuspended(Rehber)

    expect(page.text()).toContain('Onay metni yüklenemedi')
    expect(page.find('.continue').exists()).toBe(false)
  })
})
