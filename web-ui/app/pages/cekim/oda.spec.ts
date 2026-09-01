// @vitest-environment nuxt
/**
 * The capture screen (§2.4–2.5, BOYA-42).
 *
 * The promises being tested are the customer's, not the widgets'. §2.4 opens with "Listeden alan
 * seçilir" — the customer picks the area, and then which frame of it to take. They are walking their
 * own home in whatever order the doors happen to be in, and a screen that insists on the fourth wall
 * of the living room before the kitchen is arguing with somebody holding the phone.
 *
 * The rest: that a bad frame is refused *now*, while they are still in the room, and not uploaded;
 * that an accepted frame reaches storage; and that where they have got to survives the page being
 * reloaded on another device, because it is read from the server rather than remembered here.
 */
import { mockNuxtImport, mountSuspended } from '@nuxt/test-utils/runtime'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Oda from './oda.vue'

const get = vi.fn()
const post = vi.fn()
const del = vi.fn()
const { navigate } = vi.hoisted(() => ({ navigate: vi.fn() }))
const { process, upload } = vi.hoisted(() => ({ process: vi.fn(), upload: vi.fn() }))

mockNuxtImport('useApi', () => () => ({ GET: get, POST: post, DELETE: del }))
mockNuxtImport('navigateTo', () => navigate)
mockNuxtImport('useRoute', () => () => ({ query: { talep: 'draft-1' } }))
mockNuxtImport('processFrame', () => process)
mockNuxtImport('uploadFrame', () => upload)

/** A 2+1's living room and bathroom: §2.4's five frames and two. */
function state(taken: string[] = []) {
  const frame = (role: string, photoId: string | null = null) => ({
    role, photoId, taken: taken.includes(role), lowQualityFlag: false,
  })
  const living = ['WALL_1', 'WALL_2', 'WALL_3', 'WALL_4', 'CEILING']
    .map(r => frame(r, taken.includes(r) ? `photo-${r}` : null))
  const bath = ['WALL_1', 'CEILING'].map(r => frame(r))
  return {
    areas: [
      { id: 'room-living', type: 'LIVING_ROOM', label: 'Salon', sortOrder: 0, frames: living,
        complete: living.every(f => f.taken) },
      { id: 'room-bath', type: 'BATHROOM', label: 'Banyo', sortOrder: 1, frames: bath, complete: false },
    ],
    required: 7,
    taken: taken.length,
    complete: false,
  }
}

const ACCEPTED = {
  blob: new Blob([new Uint8Array(32)], { type: 'image/jpeg' }),
  measurements: { width: 2048, height: 1536, byteSize: 32, qualityScore: 400, lowQualityFlag: false },
  verdict: { accept: true, reason: null, lowQualityFlag: false },
}

/** Tap a frame's own button, then hand the picker a file — the two halves of taking one frame. */
async function shoot(page: Awaited<ReturnType<typeof mountSuspended>>, role = 'WALL_1') {
  await page.find(`.shoot[data-role="${role}"]`).trigger('click')
  await chooseAFile(page)
}

async function chooseAFile(page: Awaited<ReturnType<typeof mountSuspended>>) {
  const input = page.find('.camera')
  Object.defineProperty(input.element, 'files', {
    value: [new File([new Uint8Array(8)], 'frame.jpg', { type: 'image/jpeg' })],
    configurable: true,
  })
  await input.trigger('change')
  await new Promise(resolve => setTimeout(resolve, 0))
  await page.vm.$nextTick()
}

describe('çekim ekranı', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    clearNuxtData(() => true)
    get.mockResolvedValue({ response: { ok: true, status: 200 }, data: state() })
    post.mockImplementation(async (path: string) => (path === '/api/photos/upload-intent'
      ? { response: { ok: true, status: 201 },
          data: { photoId: 'photo-new', uploadUrl: 'https://storage.test/put', expiresInSeconds: 900 } }
      : { response: { ok: true, status: 200 }, data: {} }))
    del.mockResolvedValue({ response: { ok: true, status: 204 }, data: null })
    process.mockResolvedValue(ACCEPTED)
    upload.mockResolvedValue(undefined)
  })

  it('opens on the first area that still owes frames, without insisting on it', async () => {
    const page = await mountSuspended(Oda)

    expect(page.text()).toContain('0 / 7 fotoğraf')
    expect(page.find('[data-area="room-living"][aria-expanded="true"]').exists()).toBe(true)
  })

  it('lets the customer open a different area: §2.4 says the area is picked from the list', async () => {
    const page = await mountSuspended(Oda)

    await page.find('[data-area="room-bath"]').trigger('click')

    expect(page.find('[data-area="room-bath"][aria-expanded="true"]').exists()).toBe(true)
    // A bathroom asks for a general view, not a numbered wall (§2.4's table).
    expect(page.text()).toContain('Genel görünüm')
  })

  it('offers every outstanding frame of the open area, so the customer picks which one', async () => {
    const page = await mountSuspended(Oda)

    expect(page.findAll('[data-area="room-living"] ~ .frames .shoot')).toHaveLength(5)
  })

  it('photographs the frame that was tapped, not the one the system would have chosen next', async () => {
    const page = await mountSuspended(Oda)

    // The ceiling, while four walls are still outstanding above it.
    await page.find('.shoot[data-role="CEILING"]').trigger('click')
    await chooseAFile(page)

    expect(process).toHaveBeenCalledWith(expect.anything(), 'CEILING', 1)
    expect(post).toHaveBeenCalledWith('/api/photos/upload-intent', {
      body: { roomId: 'room-living', role: 'CEILING' },
    })
  })

  it('photographs into the area the customer opened, not the first one', async () => {
    const page = await mountSuspended(Oda)

    await page.find('[data-area="room-bath"]').trigger('click')
    await page.find('.shoot[data-role="WALL_1"]').trigger('click')
    await chooseAFile(page)

    expect(post).toHaveBeenCalledWith('/api/photos/upload-intent', {
      body: { roomId: 'room-bath', role: 'WALL_1' },
    })
  })

  it('refuses a blurred frame on the phone and does not upload it', async () => {
    process.mockResolvedValue({ ...ACCEPTED,
      verdict: { accept: false, reason: 'BLURRY', lowQualityFlag: false } })
    const page = await mountSuspended(Oda)

    await shoot(page)

    expect(page.text()).toContain('bulanık')
    expect(post).not.toHaveBeenCalled()
    expect(upload).not.toHaveBeenCalled()
  })

  it('counts attempts per frame, so §9 gives up after three rather than three times over', async () => {
    process.mockResolvedValue({ ...ACCEPTED,
      verdict: { accept: false, reason: 'DARK', lowQualityFlag: false } })
    const page = await mountSuspended(Oda)

    await shoot(page)
    await shoot(page)

    expect(process).toHaveBeenNthCalledWith(1, expect.anything(), 'WALL_1', 1)
    expect(process).toHaveBeenNthCalledWith(2, expect.anything(), 'WALL_1', 2)
  })

  it('reserves, uploads and completes an accepted frame, then re-reads where it has got to', async () => {
    const page = await mountSuspended(Oda)

    await shoot(page)

    expect(post).toHaveBeenCalledWith('/api/photos/upload-intent', {
      body: { roomId: 'room-living', role: 'WALL_1' },
    })
    expect(upload).toHaveBeenCalledWith('https://storage.test/put', ACCEPTED.blob, expect.anything())
    expect(post).toHaveBeenCalledWith('/api/photos/{id}/complete', {
      params: { path: { id: 'photo-new' } },
      body: ACCEPTED.measurements,
    })
    // Where the capture has got to is the server's answer, never a counter kept here (§10).
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('sends the customer back to the notice when consent is missing, instead of retrying', async () => {
    post.mockResolvedValue({ response: { ok: false, status: 403 }, data: null })
    const page = await mountSuspended(Oda)

    await shoot(page)

    expect(page.text()).toContain('çekim onayını')
    expect(upload).not.toHaveBeenCalled()
  })

  it('says so when the upload fails, rather than pretending the frame arrived', async () => {
    upload.mockRejectedValue(new Error('the connection dropped'))
    const page = await mountSuspended(Oda)

    await shoot(page)

    expect(page.text()).toContain('gönderilemedi')
  })

  it('retakes a frame by deleting it first: §9 will not overwrite one that arrived', async () => {
    get.mockResolvedValue({ response: { ok: true, status: 200 }, data: state(['WALL_1']) })
    const page = await mountSuspended(Oda)

    await page.find('.retake').trigger('click')
    await new Promise(resolve => setTimeout(resolve, 0))

    expect(del).toHaveBeenCalledWith('/api/photos/{id}', { params: { path: { id: 'photo-WALL_1' } } })
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('shows the whole list throughout, so the customer can see what is still owed', async () => {
    const page = await mountSuspended(Oda)

    const areas = page.findAll('.area')
    expect(areas).toHaveLength(2)
    expect(areas[0]!.text()).toContain('0/5')
    expect(areas[1]!.text()).toContain('0/2')
  })

  it('stops asking for photographs once every frame is in', async () => {
    const everything = state(['WALL_1', 'WALL_2', 'WALL_3', 'WALL_4', 'CEILING'])
    everything.areas[1]!.frames.forEach((f) => { f.taken = true })
    everything.areas[1]!.complete = true
    get.mockResolvedValue({ response: { ok: true, status: 200 },
      data: { ...everything, taken: 7, complete: true } })
    const page = await mountSuspended(Oda)

    expect(page.text()).toContain('Bütün fotoğraflar tamam')
    expect(page.find('.shoot').exists()).toBe(false)
  })

  it('says the list could not be loaded rather than showing an empty camera', async () => {
    get.mockResolvedValue({ response: { ok: false, status: 500 }, data: null })
    const page = await mountSuspended(Oda)

    expect(page.text()).toContain('yüklenemedi')
    expect(page.find('.shoot').exists()).toBe(false)
  })
})
