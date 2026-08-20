// @vitest-environment nuxt
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PriceBookList from './index.vue'

/**
 * The price list screen (BOYA-21). A Nuxt-environment test, which the rest of the suite avoids on
 * purpose — but this page *is* its wiring: what it lists, what it marks live, and what it calls when
 * the operator taps. A pure test of a helper would prove none of that.
 *
 * The acceptance criterion is that the panel works on a phone, which no assertion can check. What is
 * asserted here is the part that would break silently: the live version being marked, the age being
 * shown, and activation hitting the endpoint with the right id.
 */
const activate = vi.fn()
const versions = vi.fn()

mockNuxtImport('useApi', () => () => ({
  GET: versions,
  POST: activate,
}))

describe('price list versions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // useAsyncData caches by key across mounts, so without this the second test in the file asserts
    // against the first test's payload — which is how a stubbed 401 kept rendering a list.
    clearNuxtData('price-books')
    // The live version is deliberately NOT the newest: date order alone would put the draft first, so
    // this is the data that can tell "live first" apart from "newest first".
    versions.mockResolvedValue({
      response: { ok: true, status: 200 },
      data: [
        {
          id: '11111111-1111-7111-8111-111111111111',
          versionCode: 'REAL-2026-01',
          active: true,
          createdAt: new Date(Date.now() - 200 * 24 * 3600 * 1000).toISOString(),
        },
        {
          id: '22222222-2222-7222-8222-222222222222',
          versionCode: 'REAL-2026-02',
          active: false,
          createdAt: new Date(Date.now() - 24 * 3600 * 1000).toISOString(),
        },
        {
          id: '33333333-3333-7333-8333-333333333333',
          versionCode: 'SEED-2026-01',
          active: false,
          createdAt: new Date(Date.now() - 400 * 24 * 3600 * 1000).toISOString(),
        },
      ],
    })
    activate.mockResolvedValue({ response: { ok: true, status: 200 }, data: {} })
  })

  it('lists every version with its age, live one first', async () => {
    const page = await mountSuspended(PriceBookList)

    const codes = page.findAll('.code').map(node => node.text())
    expect(codes)
      .toEqual(['REAL-2026-01', 'REAL-2026-02', 'SEED-2026-01'])
    expect(page.text()).toContain('6 ay önce')
    expect(page.text()).toContain('dün')
    expect(page.text()).toContain('1 yıl önce')
  })

  it('marks the version quotes are priced against', async () => {
    const page = await mountSuspended(PriceBookList)

    const live = page.findAll('li').filter(node => node.attributes('data-active') === 'true')
    expect(live).toHaveLength(1)
    expect(live[0]!.text()).toContain('REAL-2026-01')
  })

  it('warns when the live list is older than the quarter §6 plans around', async () => {
    const page = await mountSuspended(PriceBookList)

    expect(page.find('.warn').exists()).toBe(true)
  })

  it('says the operator is not logged in rather than showing an empty price book', async () => {
    // A 401 rendered as "there are no price lists" tells the operator the database is empty when the
    // truth is that nobody is logged in. The panel has no login screen yet, so this message is the
    // only thing standing between that state and a wrong conclusion.
    versions.mockResolvedValue({ response: { ok: false, status: 401 }, data: undefined })

    const page = await mountSuspended(PriceBookList)

    expect(page.text()).toContain('Operatör girişi gerekiyor')
    expect(page.text()).not.toContain('Henüz fiyat listesi yok')
  })

  it('offers activation only for versions that are not live, and calls it with that id', async () => {
    const page = await mountSuspended(PriceBookList)

    const rows = page.findAll('li')
    expect(page.findAll('button')).toHaveLength(2)

    const draft = rows.find(row => row.text().includes('REAL-2026-02'))!
    await draft.find('button').trigger('click')

    expect(activate).toHaveBeenCalledWith('/api/op/price-books/{id}/activate', {
      params: { path: { id: '22222222-2222-7222-8222-222222222222' } },
    })
  })
})
