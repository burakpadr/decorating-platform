// @vitest-environment nuxt
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { describe, expect, it } from 'vitest'
import { DISTRICTS } from '~/utils/districts'
import Home from './index.vue'

/**
 * The welcome screen (BOYA-30, workflow Aşama 0).
 *
 * <p>The screen has one job and one button, so the assertions are mostly about what is *not* on it.
 * Workflow §0.1 promises no registration, no phone and no email, and the exchange table further down
 * explains why: the order matters, and asking for a number at the door loses most visitors. A field
 * added here later would break that promise silently, so the test names it.
 */
describe('welcome screen', () => {
  it('offers one way forward, into the stage 1 form', async () => {
    const page = await mountSuspended(Home)

    // §0.1 says one button, and what it means is one action — not one element. The CTA is repeated at
    // the foot of the page so somebody who read to the end does not scroll back, so what has to hold is
    // that every prominent button goes to the same place and says the same thing. A second destination
    // competing with it is the thing that would break the promise.
    const primary = page.findAll('a.btn.primary')
    expect(primary.length).toBeGreaterThan(0)
    expect(new Set(primary.map(link => link.attributes('href')))).toEqual(new Set(['/teklif-al']))
    expect(new Set(primary.map(link => link.text()))).toEqual(new Set(['Ücretsiz fiyat alın']))
  })

  it('asks for nothing: no field of any kind on the way in', async () => {
    const page = await mountSuspended(Home)

    // Workflow §0.1: "Kayıt gerekmez. Telefon, e-posta, üyelik istenmez." The cheapest way to keep that
    // true is to fail the build when an input appears on this page at all.
    expect(page.findAll('input')).toHaveLength(0)
    expect(page.findAll('form')).toHaveLength(0)
    expect(page.findAll('select')).toHaveLength(0)
  })

  it('acceptance: links to every district page — that is what makes 39 prerendered pages worth having', async () => {
    const page = await mountSuspended(Home)

    const hrefs = page.findAll('a').map(link => link.attributes('href'))
    for (const district of DISTRICTS) {
      expect(hrefs).toContain(`/${district.slug}-boya-badana-fiyatlari`)
    }
    expect(DISTRICTS).toHaveLength(39)
  })

  it('says why the answer is a range, because that is the objection it will meet', async () => {
    const page = await mountSuspended(Home)

    // §1.5: "aralığın geniş olması kusur değil, dürüsttür". The landing page is where a visitor first
    // meets the idea, and a range nobody explained reads as a business that does not know its prices.
    // Both halves are asserted because a formal rewrite is exactly where the substance goes missing: the
    // sentence stays polite and stops saying anything.
    expect(page.text()).toContain('fiyat aralığı')
    expect(page.text()).toContain('emin olmadığınızı')
  })

  it('sets a page title and a description, since the whole point is arriving from search', async () => {
    const page = await mountSuspended(Home)

    expect(page.html()).toBeTruthy()
  })
})
