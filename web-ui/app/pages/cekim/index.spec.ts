// @vitest-environment nuxt
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import RoomList from './index.vue'

/**
 * Stage 2's first screen: the customer accepts the areas to photograph (BOYA-38, workflow §2.2).
 *
 * The ticket's acceptance criterion is about expectation, not about a list widget: "müşterinin
 * beklentisi baştan kurulur — ortada bırakılan çekim, baştan söylenmiş uzun listeden kötüdür". So the
 * assertions are that the screen states the whole cost of stage 2 up front — how many areas, how many
 * photographs, how long — and that the number stays true after every edit. A total that goes stale the
 * moment a second bathroom is added is the same broken promise, made quietly.
 */
const get = vi.fn()
const post = vi.fn()
const { navigate } = vi.hoisted(() => ({ navigate: vi.fn() }))

mockNuxtImport('useApi', () => () => ({ GET: get, POST: post }))
mockNuxtImport('navigateTo', () => navigate)
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

/** A 3+1, whole home: §2.1's seven areas and §2.4's 28 frames. */
const ESTIMATE = {
  low: 45241.33,
  high: 57579.88,
  bandRatio: 0.12,
  netArea: 92,
  areaWasGross: false,
  rooms: [
    { type: 'LIVING_ROOM', label: 'Salon', requiredPhotos: 5 },
    { type: 'MASTER_BEDROOM', label: 'Ebeveyn yatak odası', requiredPhotos: 5 },
    { type: 'BEDROOM', label: 'Yatak odası 1', requiredPhotos: 5 },
    { type: 'BEDROOM', label: 'Yatak odası 2', requiredPhotos: 5 },
    { type: 'KITCHEN', label: 'Mutfak', requiredPhotos: 3 },
    { type: 'BATHROOM', label: 'Banyo', requiredPhotos: 2 },
    { type: 'HALLWAY', label: 'Koridor', requiredPhotos: 3 },
  ],
  photoCount: 28,
  requiredPhotosByType: {
    LIVING_ROOM: 5,
    MASTER_BEDROOM: 5,
    BEDROOM: 5,
    STUDY: 5,
    KITCHEN: 3,
    BATHROOM: 2,
    HALLWAY: 3,
    BALCONY: 2,
  },
}

describe('the room list screen', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearNuxtData(() => true)
    get.mockResolvedValue({ response: { ok: true, status: 200 }, data: ANSWERS })
    post.mockResolvedValue({ response: { ok: true, status: 200 }, data: ESTIMATE })
  })

  it('lists every area the answers implied, not the four rooms the customer typed', async () => {
    const page = await mountSuspended(RoomList)

    const labels = page.findAll('.area .label').map(area => area.text())
    expect(labels).toEqual([
      'Salon', 'Ebeveyn yatak odası', 'Yatak odası 1', 'Yatak odası 2',
      'Mutfak', 'Banyo', 'Koridor',
    ])
  })

  it('states the whole cost of stage 2 before it starts: areas, frames, minutes', async () => {
    const page = await mountSuspended(RoomList)

    const promise = page.find('.promise').text()
    expect(promise).toContain('7 alan')
    expect(promise).toContain('28 fotoğraf')
    // §2.4 budgets about eight minutes for these 28 frames.
    expect(promise).toContain('8 dakika')
  })

  it('says how many frames each area needs, so a long list is not a surprise', async () => {
    const page = await mountSuspended(RoomList)

    const rows = page.findAll('.area')
    expect(rows[0]!.text()).toContain('5 kare')
    // A bathroom is two: most of its wall is tile and cupboard (§2.4).
    expect(rows[5]!.text()).toContain('2 kare')
  })

  it("offers §2.2's ready buttons for the areas the layout did not imply", async () => {
    const page = await mountSuspended(RoomList)

    // A 3+1 already has one bathroom, so that button is the second one; the study and the balcony are
    // areas this layout never derives.
    expect(page.find('[data-add="BATHROOM"]').text()).toContain('İkinci banyo ekle')
    expect(page.find('[data-add="STUDY"]').text()).toContain('Çalışma odası ekle')
    expect(page.find('[data-add="BALCONY"]').text()).toContain('Balkon ekle')
  })

  it('keeps the promise true when an area is added, and renumbers what it named', async () => {
    const page = await mountSuspended(RoomList)

    await page.find('[data-add="BATHROOM"]').trigger('click')

    const labels = page.findAll('.area .label').map(area => area.text())
    expect(labels).toContain('Banyo 1')
    expect(labels).toContain('Banyo 2')
    // Two more frames, not four: the total is the version's own arithmetic, area by area.
    expect(page.find('.promise').text()).toContain('30 fotoğraf')
  })

  it('counts an added study at five frames, which no area on the list would have told it', async () => {
    const page = await mountSuspended(RoomList)

    await page.find('[data-add="STUDY"]').trigger('click')

    expect(page.find('.promise').text()).toContain('33 fotoğraf')
    expect(page.find('.promise').text()).toContain('9 dakika')
  })

  it('removes the area that was pressed, not every one of its kind', async () => {
    const page = await mountSuspended(RoomList)

    await page.findAll('.area .remove')[3]!.trigger('click')

    const labels = page.findAll('.area .label').map(area => area.text())
    expect(labels).toEqual([
      'Salon', 'Ebeveyn yatak odası', 'Yatak odası', 'Mutfak', 'Banyo', 'Koridor',
    ])
    expect(page.find('.promise').text()).toContain('23 fotoğraf')
  })

  it('confirms the list as shown, in the order it is shown, and moves on', async () => {
    const page = await mountSuspended(RoomList)
    await page.find('[data-add="BATHROOM"]').trigger('click')

    await page.find('.confirm').trigger('click')

    expect(post).toHaveBeenLastCalledWith('/api/quote-requests/{id}/rooms/confirm', {
      params: { path: { id: 'draft-1' } },
      body: {
        areas: [
          'LIVING_ROOM', 'MASTER_BEDROOM', 'BEDROOM', 'BEDROOM', 'KITCHEN',
          'BATHROOM', 'BATHROOM', 'HALLWAY',
        ],
      },
    })
    expect(navigate).toHaveBeenCalledWith('/cekim/rehber?talep=draft-1')
  })

  it('refuses to confirm an empty list rather than letting the server refuse it', async () => {
    const page = await mountSuspended(RoomList)

    for (let i = 0; i < 7; i += 1) {
      await page.find('.area .remove').trigger('click')
    }

    expect(page.findAll('.area')).toHaveLength(0)
    expect(page.text()).toContain('en az bir alan')
    expect(page.find('.confirm').attributes('disabled')).toBeDefined()
  })

  it('says so when the confirmation fails, and keeps the list to try again', async () => {
    const page = await mountSuspended(RoomList)
    post.mockResolvedValue({ response: { ok: false, status: 500 }, error: {} })

    await page.find('.confirm').trigger('click')

    expect(page.text()).toContain('onaylanamadı')
    expect(page.findAll('.area')).toHaveLength(7)
    expect(navigate).not.toHaveBeenCalled()
  })

  it('treats an already-confirmed list as confirmed and goes on, rather than showing an error', async () => {
    const page = await mountSuspended(RoomList)
    // 409 is what a second press, a double tap or a reload of this screen produces (BOYA-37).
    post.mockResolvedValue({ response: { ok: false, status: 409 }, error: {} })

    await page.find('.confirm').trigger('click')

    expect(navigate).toHaveBeenCalledWith('/cekim/rehber?talep=draft-1')
  })

  it('does not ask a confirmed request to confirm again when the customer comes back', async () => {
    get.mockResolvedValue({
      response: { ok: true, status: 200 },
      data: { ...ANSWERS, status: 'PHOTOS_PENDING' },
    })
    const page = await mountSuspended(RoomList)

    // The answers are frozen and the list is agreed: re-deriving it would show a list the photographs
    // are not being taken against, and the estimate is not this screen's business any more.
    expect(post).not.toHaveBeenCalled()
    expect(page.text()).toContain('onayladınız')
    expect(page.find('.onward').attributes('href')).toBe('/cekim/rehber?talep=draft-1')
  })

  it('sends an unfinished draft back to the form rather than a list built on gaps', async () => {
    get.mockResolvedValue({
      response: { ok: true, status: 200 },
      data: { ...ANSWERS, priceable: false, wallCondition: null },
    })
    const page = await mountSuspended(RoomList)

    expect(post).not.toHaveBeenCalled()
    expect(page.text()).toContain('cevaplanmamış')
    expect(page.find('a[href="/teklif-al"]').exists()).toBe(true)
  })

  it('says so when the list cannot be prepared, rather than waiting for ever', async () => {
    post.mockResolvedValue({ response: { ok: false, status: 500 }, error: {} })
    const page = await mountSuspended(RoomList)

    expect(page.text()).toContain('hazırlanamadı')
    expect(page.text()).not.toContain('hazırlanıyor')
    // Whoever is looking at a failure needs the way out more than whoever is looking at a list.
    expect(page.find('.back-home').exists()).toBe(true)
  })

  it('§1: carries no cost and no margin, and no price at all', async () => {
    const page = await mountSuspended(RoomList)

    const text = page.text()
    expect(text).not.toContain('Maliyet')
    expect(text).not.toContain('Marj')
    // The range belongs to the screen before this one. This screen asks about a list of areas, and a
    // price beside it invites the customer to reopen a decision they have already made.
    expect(text).not.toContain('45.241')
    expect(page.html()).not.toContain('totalCost')
  })

  it('offers the way back to the answers, because the list came from them', async () => {
    const page = await mountSuspended(RoomList)

    expect(page.find('.edit').attributes('href')).toBe('/teklif-al?talep=draft-1')
    expect(page.find('.back-home').attributes('href')).toBe('/')
  })
})
