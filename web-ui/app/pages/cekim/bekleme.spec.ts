// @vitest-environment nuxt
/**
 * §3.2's waiting screen (BOYA-46).
 *
 * Two things are being tested and the second is the one that gets forgotten: that the promise is said
 * in the right words, and that the customer is told they may close the screen. §3.2 asks for both in
 * one sentence, and a screen that says only the first leaves somebody refreshing it.
 */
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { describe, expect, it, vi } from 'vitest'

import Bekleme from './bekleme.vue'

const { query } = vi.hoisted(() => ({ query: { value: {} as Record<string, string> } }))
mockNuxtImport('useRoute', () => () => ({ query: query.value }))

describe('bekleme ekranı', () => {
  it('says when, and that the screen can be closed', async () => {
    vi.setSystemTime(new Date('2026-09-01T20:00:00Z'))
    query.value = { talep: 'draft-1', yanit: '2026-09-02T08:00:00Z' }

    const page = await mountSuspended(Bekleme)

    // §3.2's own example: asked at 23:00 Istanbul, answered tomorrow morning.
    expect(page.text()).toContain('yarın')
    expect(page.text()).toContain('11:00')
    expect(page.text()).toContain('kapatabilirsiniz')
    vi.useRealTimers()
  })

  it('says nothing about the hour when it was not told one, rather than inventing it', async () => {
    query.value = { talep: 'draft-1' }

    const page = await mountSuspended(Bekleme)

    expect(page.text()).toContain('haber vereceğiz')
    expect(page.text()).not.toContain('saat')
  })

  it('explains what happens next, so the wait is not a blank', async () => {
    query.value = { talep: 'draft-1', yanit: '2026-09-01T11:00:00Z' }

    const page = await mountSuspended(Bekleme)

    expect(page.findAll('.next li')).toHaveLength(3)
  })

  it('describes the work, not a person doing it', async () => {
    query.value = { talep: 'draft-1', yanit: '2026-09-01T11:00:00Z' }

    const page = await mountSuspended(Bekleme)

    const steps = page.findAll('.next li').map(li => li.text()).join(' ')
    // §4.1 splits the two halves and the copy has to keep them apart: the analysis reads the walls,
    // a deterministic engine works out the money. "Yapay zekâ fiyat veriyor" would describe a system
    // this one is deliberately not, and the traceability of every figure rests on that split.
    expect(steps).toContain('inceleniyor')
    expect(steps).toContain('hesaplanıyor')
    // The operator approval is real (§11's only human control point) but the customer is told what
    // happens, not who does it.
    expect(steps).not.toContain('Yetkilimiz')
  })

  it('asks for a request rather than showing a promise about nothing', async () => {
    query.value = {}

    const page = await mountSuspended(Bekleme)

    expect(page.text()).toContain('belli değil')
  })
})
