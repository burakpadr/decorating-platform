<script setup lang="ts">
/**
 * Stage 2's first screen: the areas to photograph, before a single photograph is taken (§2.2, BOYA-38).
 *
 * §2.2 gives the reason this screen exists at all. "3+1" is four rooms to the person who typed it and
 * seven areas to us, because painting a whole home includes the kitchen, the bathroom and the hallway —
 * and "ortada bırakılan çekim, baştan söylenmiş uzun listeden kötüdür". So the screen states the whole
 * cost of stage 2 up front: how many areas, how many photographs, how many minutes.
 *
 * The customer edits the list here, which is why the count and the labels are computed on the client:
 * the list changes under their hands and the server has not seen it yet. It labels the same list back
 * on confirm, by the same rule — `areaList.spec.ts` is what asks the two to stay in step.
 *
 * What is *not* computed here is how many frames a kind of area needs: that belongs to the price book
 * version the range was priced with (§5.3) and arrives with the estimate, so an added study costs its
 * own five frames rather than an average nobody chose.
 */
import { addArea, captureMinutes, labelAreas, photoTotal, removeAreaAt } from '~/utils/areaList'
import type { RoomTypeCode } from '~/utils/layoutAreas'

const { t } = useI18n()
const api = useApi()
const route = useRoute()

useHead({ title: t('meta.titleTemplate', { page: t('roomList.title') }) })

const id = String(route.query.talep ?? '')

/**
 * §2.2's ready buttons, minus the one that has nowhere to go.
 *
 * The workflow names four — ikinci banyo, çalışma odası, giyinme odası, balkon — and a dressing room is
 * not a room type: it has no row in `room_type_config`, so nothing knows how much of it is paintable or
 * how many frames it needs. Offering it would mean labelling it as something else on the customer's own
 * capture screen. It needs a price book row first, which is a version change, not a screen change.
 */
const READY_TO_ADD: RoomTypeCode[] = ['BATHROOM', 'STUDY', 'BALCONY']

const { data: draft, error: draftError } = await useAsyncData(`draft-${id}`, async () => {
  const { data, response } = await api.GET('/api/quote-requests/{id}',
    { params: { path: { id } } })
  if (!response.ok) {
    throw new Error('draft')
  }
  return data ?? null
})

/**
 * The derived list, from the same call that produced the range.
 *
 * Only while the request is still a draft. Once the list has been confirmed the answers are frozen and
 * the photographs are being taken against the agreed list — re-deriving it here would put a different
 * list on screen from the one the capture flow is working through.
 */
const { data: estimate, error: estimateError } = await useAsyncData(`rooms-${id}`, async () => {
  if (!draft.value?.priceable || draft.value.status !== 'DRAFT') {
    return null
  }
  const { data, response } = await api.POST('/api/quote-requests/{id}/estimate',
    { params: { path: { id } } })
  if (!response.ok) {
    throw new Error('estimate')
  }
  return data ?? null
}, { watch: [draft] })

const confirmed = computed(() => Boolean(draft.value) && draft.value?.status !== 'DRAFT')
const onward = computed(() => `/cekim/rehber?talep=${id}`)

/** The list as the customer has it now: types in capture order, duplicates and all. */
const areas = ref<RoomTypeCode[]>([])
watch(estimate, (value) => {
  if (value) {
    areas.value = value.rooms.map(room => room.type)
  }
}, { immediate: true })

/** How many frames each kind of area needs, in the version that priced this request. */
const frames = computed<Record<string, number>>(() => estimate.value?.requiredPhotosByType ?? {})

const areaName = (type: RoomTypeCode) => t(`rooms.${type}`)
const listed = computed(() => labelAreas(areas.value, areaName))
const photoCount = computed(() => photoTotal(areas.value, frames.value))
const minutes = computed(() => captureMinutes(photoCount.value))

/** Only the kinds this version knows: a button for an area nothing can price is a button that lies. */
const addable = computed(() => READY_TO_ADD.filter(type => type in frames.value))

/**
 * "Çalışma odası ekle" when there is none, "İkinci banyo ekle" when there is one.
 *
 * §2.2 names the second bathroom specifically, and it is the case that needs naming: a button reading
 * "Banyo ekle" next to a bathroom already on the list reads as though the list is missing one.
 */
function addLabel(type: RoomTypeCode): string {
  const already = areas.value.filter(area => area === type).length
  const name = areaName(type)
  if (already === 0) {
    return t('roomList.add', { area: name })
  }
  const lower = name.toLocaleLowerCase('tr')
  return already === 1
    ? t('roomList.addSecond', { area: lower })
    : t('roomList.addAnother', { area: lower })
}

const busy = ref(false)
const failed = ref(false)

async function confirm() {
  if (busy.value || areas.value.length === 0) {
    return
  }
  busy.value = true
  failed.value = false
  try {
    const { response } = await api.POST('/api/quote-requests/{id}/rooms/confirm', {
      params: { path: { id } },
      body: { areas: areas.value },
    })
    // 409 is the answer to a second press, a double tap or a reload: the list is already agreed, which
    // is what this screen was asking for. Showing an error would send the customer back to redo a
    // decision the server has already recorded.
    if (!response.ok && response.status !== 409) {
      failed.value = true
      return
    }
    await navigateTo(onward.value)
  }
  catch {
    failed.value = true
  }
  finally {
    busy.value = false
  }
}
</script>

<template>
  <main>
    <!-- Above every state, in the corner: somebody looking at "hazırlanamadı" needs it more than
         somebody looking at a list. -->
    <NuxtLink class="back-home" to="/">{{ t('roomList.leave') }}</NuxtLink>

    <template v-if="draftError">
      <section class="panel">
        <p>{{ t('roomList.failed') }}</p>
        <NuxtLink class="btn" to="/teklif-al">{{ t('roomList.goToForm') }}</NuxtLink>
      </section>
    </template>

    <template v-else-if="draft && !draft.priceable">
      <section class="panel">
        <p>{{ t('roomList.incomplete') }}</p>
        <NuxtLink class="btn primary" to="/teklif-al">{{ t('roomList.goToForm') }}</NuxtLink>
      </section>
    </template>

    <!-- Already agreed. The list is not re-derived and not re-confirmed: it is the one the photographs
         are being taken against. -->
    <template v-else-if="confirmed">
      <section class="panel">
        <p>{{ t('roomList.already') }}</p>
        <NuxtLink class="btn primary onward" :to="onward">{{ t('roomList.continue') }}</NuxtLink>
      </section>
    </template>

    <template v-else-if="estimateError">
      <section class="panel">
        <p>{{ t('roomList.failed') }}</p>
        <NuxtLink class="btn" to="/teklif-al">{{ t('roomList.goToForm') }}</NuxtLink>
      </section>
    </template>

    <template v-else-if="estimate">
      <section class="head">
        <p class="eyebrow">{{ t('roomList.eyebrow') }}</p>
        <h1>{{ t('roomList.title') }}</h1>
        <!-- The whole cost of stage 2, in the three numbers that decide whether somebody starts it. -->
        <p class="promise num">
          {{ t('roomList.promise', {
            areas: listed.length, photos: photoCount, minutes,
          }) }}
        </p>
        <p class="intro">{{ t('roomList.intro') }}</p>
      </section>

      <section class="panel list">
        <ul>
          <li v-for="(area, index) in listed" :key="`${area.type}-${index}`" class="area">
            <span class="label">{{ area.label }}</span>
            <span class="frames num">{{ t('roomList.frames', { count: frames[area.type] ?? 0 }) }}</span>
            <button class="remove" type="button" @click="areas = removeAreaAt(areas, index)">
              {{ t('roomList.remove') }}
            </button>
          </li>
        </ul>

        <p v-if="listed.length === 0" class="empty">{{ t('roomList.empty') }}</p>
      </section>

      <section class="panel add">
        <h2>{{ t('roomList.addTitle') }}</h2>
        <div class="add-buttons">
          <button
            v-for="type in addable" :key="type" class="btn outline" type="button"
            :data-add="type" @click="areas = addArea(areas, type)"
          >
            {{ addLabel(type) }}
          </button>
        </div>
      </section>

      <p v-if="failed" class="err" role="alert">{{ t('roomList.confirmFailed') }}</p>

      <div class="actions">
        <button
          class="btn primary confirm" type="button" :disabled="busy || listed.length === 0"
          @click="confirm"
        >
          {{ busy ? t('roomList.confirming') : t('roomList.confirm') }}
        </button>
        <!-- The list came from the answers, so the way to change it is the way back to them. -->
        <NuxtLink class="btn outline edit" :to="`/teklif-al?talep=${id}`">
          {{ t('roomList.edit') }}
        </NuxtLink>
      </div>
      <p class="hint">{{ t('roomList.confirmNote') }}</p>
    </template>

    <p v-else class="panel">{{ t('roomList.loading') }}</p>
  </main>
</template>

<style scoped>
main {
  max-width: 34rem;
  margin: 0 auto;
  padding: 1.5rem 1.25rem 4rem;
  display: grid;
  gap: var(--gap-section);
  color: var(--ink);
  font-family: var(--sans);
}

.back-home {
  justify-self: start;
  color: var(--ink-3);
  font-size: 0.85rem;
  font-weight: 550;
  text-decoration: none;
}

.back-home::before {
  content: '← ';
}

.back-home:hover {
  color: var(--ink);
}

.eyebrow {
  margin: 0 0 var(--gap);
  font-size: 0.75rem;
  font-weight: 650;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--brand);
}

h1 {
  margin: 0;
  font-size: 1.4rem;
  letter-spacing: -0.01em;
}

/* The three numbers that decide whether stage 2 gets started, at the size of an answer. */
.promise {
  margin: var(--gap-loose) 0 0;
  font-family: var(--mono);
  font-size: 1.05rem;
  font-variant-numeric: tabular-nums;
  font-weight: 650;
}

.intro {
  margin: var(--gap-loose) 0 0;
  font-size: 0.95rem;
  color: var(--ink-2);
}

.panel {
  padding: var(--gap-section);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.list ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.area {
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: var(--gap-section);
  padding: var(--gap-loose) 0;
  border-bottom: 1px solid var(--line);
}

.area:last-child {
  border-bottom: 0;
}

.label {
  font-size: 1rem;
}

.frames {
  font-family: var(--mono);
  font-size: 0.85rem;
  font-variant-numeric: tabular-nums;
  color: var(--ink-3);
}

/* Quiet, and never quieter than legible: removing an area is a decision §2.2 asks the customer to
   make, not an escape hatch. */
.remove {
  padding: 0;
  border: 0;
  border-bottom: 1px solid var(--line-strong);
  background: none;
  color: var(--ink-2);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}

.remove:hover {
  color: var(--ink);
}

.empty {
  margin: 0;
  font-size: 0.95rem;
  color: var(--danger);
}

.add h2 {
  margin: 0 0 var(--gap-loose);
  font-size: 1.05rem;
}

.add-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gap);
}

.btn {
  display: inline-flex;
  align-items: center;
  min-height: 3rem;
  padding: 0 1.4rem;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 1.05rem;
  font-weight: 650;
  text-decoration: none;
  cursor: pointer;
}

.btn.primary {
  border-color: var(--brand);
  background: var(--brand);
  color: var(--brand-ink);
}

.btn.primary:hover {
  background: var(--brand-hover);
}

.btn.primary:disabled {
  border-color: var(--line-strong);
  background: var(--line);
  color: var(--ink-3);
  cursor: not-allowed;
}

.btn.outline {
  border-color: var(--brand);
  background: var(--surface);
  color: var(--brand);
}

.btn.outline:hover {
  background: var(--brand-soft);
}

/* The add buttons are a second-rank action next to "Listeyi onayla", and there are three of them. */
.add-buttons .btn {
  min-height: 2.6rem;
  padding: 0 1rem;
  font-size: 0.95rem;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--gap-loose) var(--gap-section);
}

.hint {
  margin: 0;
  font-size: 0.85rem;
  color: var(--ink-3);
}

.err {
  margin: 0;
  font-size: 0.9rem;
  color: var(--danger);
}
</style>
