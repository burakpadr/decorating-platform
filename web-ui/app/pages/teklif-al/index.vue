<script setup lang="ts">
/**
 * Stage 1's eight questions, over three screens (workflow §1.1–1.3, BOYA-31).
 *
 * Every completed step is written to the server. §8 is the reason and it is not a preference: people
 * fill in two screens on a laptop and finish on a phone, so an answer that lived in the browser is an
 * answer they are asked for twice. The draft id goes into the URL for the same reason — a refresh must
 * not start a new one.
 *
 * The draft is created when the first screen is finished rather than when the page opens: somebody who
 * lands here and leaves has not started anything, and a row for them would be a row that looks
 * abandoned (BOYA-36 counts those).
 */
import { LAYOUT_AREAS, type LayoutCode, type RoomTypeCode } from '~/utils/layoutAreas'

const { t } = useI18n()
const api = useApi()
const route = useRoute()
const router = useRouter()

useHead({ title: t('meta.titleTemplate', { page: t('pages.quoteRequest.title') }) })

const STEPS = 3
const MIN_AREA = 20
const MAX_AREA = 500
const MAX_DOORS = 50

type AreaBasis = 'GROSS' | 'NET'
type Scope = 'WHOLE_HOME' | 'SELECTED_ROOMS'
type Furnishing = 'EMPTY' | 'PARTIAL' | 'FURNISHED'
type WallCondition = 'GOOD' | 'MINOR' | 'MAJOR' | 'UNSURE'

const step = ref(1)
const busy = ref(false)
const failure = ref('')

/**
 * Nothing starts chosen. The gross/net question is the one this matters most for: the two differ by
 * about 18%, so a default is a silent error in every square metre downstream — and §1.1 asks for the
 * measurement *with* its basis for exactly that reason.
 */
const form = reactive({
  districtCode: '',
  area: '',
  areaBasis: null as AreaBasis | null,
  layout: '' as '' | LayoutCode,
  scope: null as Scope | null,
  selectedRooms: [] as RoomTypeCode[],
  furnishing: null as Furnishing | null,
  doorsPainted: null as boolean | null,
  doorCount: '',
  doorColourChange: null as boolean | null,
  wallCondition: null as WallCondition | null,
})

const draftId = ref(typeof route.query.talep === 'string' ? route.query.talep : '')

/** Only the districts the business serves — an unserved one cannot be picked (BOYA-26, BOYA-27). */
const { data: districts, error: districtsError } = await useAsyncData('districts', async () => {
  const { data, response } = await api.GET('/api/districts')
  if (!response.ok) {
    throw new Error('districts')
  }
  return data ?? []
})

const areas = computed(() => (form.layout ? LAYOUT_AREAS[form.layout] : []))

/** Turned off when the layout changes, because the areas it named no longer exist. */
watch(() => form.layout, () => {
  form.selectedRooms = []
})

function toggleArea(type: RoomTypeCode) {
  const at = form.selectedRooms.indexOf(type)
  if (at === -1) {
    form.selectedRooms.push(type)
  }
  else {
    form.selectedRooms.splice(at, 1)
  }
}

const errors = reactive<Record<string, string>>({})

function validate(current: number): boolean {
  for (const key of Object.keys(errors)) {
    delete errors[key]
  }
  if (current === 1) {
    if (!form.districtCode) {
      errors.districtCode = t('quoteForm.required')
    }
    const area = Number(form.area)
    if (!form.area || Number.isNaN(area) || area < MIN_AREA || area > MAX_AREA) {
      errors.area = t('quoteForm.areaRange')
    }
    if (!form.areaBasis) {
      errors.areaBasis = t('quoteForm.required')
    }
    if (!form.layout) {
      errors.layout = t('quoteForm.required')
    }
    if (!form.scope) {
      errors.scope = t('quoteForm.required')
    }
    else if (form.scope === 'SELECTED_ROOMS' && form.selectedRooms.length === 0) {
      errors.selectedRooms = t('quoteForm.step1.selectedNone')
    }
  }
  if (current === 2) {
    if (!form.furnishing) {
      errors.furnishing = t('quoteForm.required')
    }
    if (form.doorsPainted === null) {
      errors.doorsPainted = t('quoteForm.required')
    }
    else if (form.doorsPainted) {
      const doors = Number(form.doorCount)
      if (!form.doorCount || Number.isNaN(doors) || doors < 1 || doors > MAX_DOORS) {
        errors.doorCount = t('quoteForm.doorCountRange')
      }
      if (form.doorColourChange === null) {
        errors.doorColourChange = t('quoteForm.required')
      }
    }
  }
  if (current === 3 && !form.wallCondition) {
    errors.wallCondition = t('quoteForm.required')
  }
  return Object.keys(errors).length === 0
}

/** What this screen answered — and only this screen, so a PATCH says what the customer just did. */
function patchFor(current: number): Record<string, unknown> {
  if (current === 1) {
    return {
      districtCode: form.districtCode,
      area: Number(form.area),
      areaBasis: form.areaBasis,
      layout: form.layout,
      scope: form.scope,
      selectedRooms: form.scope === 'SELECTED_ROOMS' ? form.selectedRooms : undefined,
    }
  }
  if (current === 2) {
    return {
      furnishing: form.furnishing,
      // Zero rather than absent: §4.2's column is nullable and null means "did not reach this screen".
      // "No doors" is an answer, and the estimate has to be able to tell the two apart.
      doorCount: form.doorsPainted ? Number(form.doorCount) : 0,
      doorColourChange: form.doorsPainted ? form.doorColourChange : false,
    }
  }
  return { wallCondition: form.wallCondition }
}

async function advance() {
  if (busy.value || !validate(step.value)) {
    return
  }
  busy.value = true
  failure.value = ''
  try {
    await save()
  }
  catch {
    // A request that throws rather than answering — a refused preflight, a dropped connection — used to
    // leave the button reading "Kaydediliyor…" for ever, with nothing said and nothing in the log. A
    // stuck button is worse than an error: the customer waits instead of retrying.
    failure.value = t('quoteForm.failed')
  }
  finally {
    busy.value = false
  }
}

async function save() {

  if (!draftId.value) {
    const { data, response } = await api.POST('/api/quote-requests')
    if (!response.ok || !data) {
      failure.value = t('quoteForm.failed')
      return
    }
    draftId.value = data.id
    // Replaced, not pushed: the id is not a place in the customer's history, it is which draft this is.
    router.replace({ query: { ...route.query, talep: data.id } })
  }

  const { response, error } = await api.PATCH('/api/quote-requests/{id}', {
    params: { path: { id: draftId.value } },
    body: patchFor(step.value) as never,
  })

  if (!response.ok) {
    // Advancing on a failed write is how an answer goes missing with nobody seeing it: the customer
    // finishes the form and the draft is one screen short.
    const problem = error as { type?: string, districtCode?: string } | undefined
    failure.value = problem?.type === 'urn:decorating:district-not-served'
      ? t('quoteForm.notServed', { district: districtName(problem.districtCode) })
      : t('quoteForm.failed')
    return
  }

  if (step.value === STEPS) {
    await navigateTo(`/teklif-al/sonuc?talep=${draftId.value}`)
    return
  }
  step.value += 1
}

function districtName(code?: string): string {
  return districts.value?.find(district => district.code === code)?.name ?? code ?? ''
}

function back() {
  failure.value = ''
  step.value = Math.max(1, step.value - 1)
}
</script>

<template>
  <main>
    <header class="head">
      <p class="progress">{{ t('quoteForm.progress', { step, total: STEPS }) }}</p>
      <div class="bar" :aria-hidden="true">
        <span :style="{ width: `${(step / STEPS) * 100}%` }" />
      </div>
    </header>

    <p v-if="districtsError" class="banner danger">{{ t('quoteForm.districtsFailed') }}</p>

    <!-- ------------------------------------------------------------------ 1 · §1.1 -->
    <section v-if="step === 1" class="panel">
      <h1>{{ t('quoteForm.step1.title') }}</h1>

      <label class="field">
        <span class="q">{{ t('quoteForm.step1.district') }}</span>
        <select v-model="form.districtCode" name="district">
          <option value="" disabled>{{ t('quoteForm.step1.districtPlaceholder') }}</option>
          <option v-for="district in districts ?? []" :key="district.code" :value="district.code">
            {{ district.name }}
          </option>
        </select>
        <em class="help">{{ t('quoteForm.step1.districtHelp') }}</em>
        <em v-if="errors.districtCode" class="err">{{ errors.districtCode }}</em>
      </label>

      <label class="field">
        <span class="q">{{ t('quoteForm.step1.area') }}</span>
        <span class="with-unit">
          <input v-model="form.area" name="area" inputmode="numeric" type="text">
          <i>m²</i>
        </span>
        <em v-if="errors.area" class="err">{{ errors.area }}</em>
      </label>

      <!-- No default. The two differ by roughly 18%, so a preselected answer is a silent error in every
           square metre after it — and the customer would have no way of seeing where it came from. -->
      <fieldset class="field">
        <legend class="q">{{ t('quoteForm.step1.areaBasis') }}</legend>
        <div class="segmented" data-group="areaBasis">
          <button
            v-for="basis in (['GROSS', 'NET'] as const)" :key="basis" type="button"
            :aria-pressed="form.areaBasis === basis" @click="form.areaBasis = basis"
          >
            {{ t(`calculate.areaBasis.${basis}`) }}
          </button>
        </div>
        <em class="help">{{ t('quoteForm.step1.areaBasisHelp') }}</em>
        <em v-if="errors.areaBasis" class="err">{{ errors.areaBasis }}</em>
      </fieldset>

      <label class="field">
        <span class="q">{{ t('quoteForm.step1.layout') }}</span>
        <select v-model="form.layout" name="layout">
          <option value="" disabled>—</option>
          <option
            v-for="code in (Object.keys(LAYOUT_AREAS) as LayoutCode[])" :key="code" :value="code"
          >
            {{ t(`layout.${code}`) }}
          </option>
        </select>
        <em v-if="errors.layout" class="err">{{ errors.layout }}</em>
      </label>

      <fieldset class="field">
        <legend class="q">{{ t('quoteForm.step1.scope') }}</legend>
        <div class="segmented" data-group="scope">
          <button
            v-for="option in (['WHOLE_HOME', 'SELECTED_ROOMS'] as const)" :key="option" type="button"
            :aria-pressed="form.scope === option" @click="form.scope = option"
          >
            {{ t(`scope.${option}`) }}
          </button>
        </div>

        <!-- "3+1" is four rooms to the person typing and seven areas to the engine. Saying so here is
             what stops the room list being a surprise at the start of stage 2 (workflow §2.2). -->
        <p v-if="form.scope === 'WHOLE_HOME' && form.layout" class="derived">
          {{ t('quoteForm.step1.scopeAreas', { layout: t(`layout.${form.layout}`) }) }}
          <span>{{ areas.map(area => t(`rooms.${area.type}`) + (area.count > 1 ? ` ×${area.count}` : '')).join(' · ') }}</span>
        </p>

        <div v-if="form.scope === 'SELECTED_ROOMS' && form.layout" class="areas">
          <p class="help">{{ t('quoteForm.step1.selectAreas') }}</p>
          <div class="chips" data-group="areas">
            <button
              v-for="area in areas" :key="area.type" type="button"
              :aria-pressed="form.selectedRooms.includes(area.type)" @click="toggleArea(area.type)"
            >
              {{ t(`rooms.${area.type}`) }}<em v-if="area.count > 1"> ×{{ area.count }}</em>
            </button>
          </div>
        </div>
        <em v-if="errors.scope" class="err">{{ errors.scope }}</em>
        <em v-if="errors.selectedRooms" class="err">{{ errors.selectedRooms }}</em>
      </fieldset>
    </section>

    <!-- ------------------------------------------------------------------ 2 · §1.2 -->
    <section v-if="step === 2" class="panel">
      <h1>{{ t('quoteForm.step2.title') }}</h1>

      <!-- The painting day, not today. Customers paint before they move in, and the answer moves the
           labour by 25% — so this is the question that has to be asked (§1.2). -->
      <fieldset class="field">
        <legend class="q">{{ t('quoteForm.step2.furnishing') }}</legend>
        <div class="segmented" data-cols="3" data-group="furnishing">
          <button
            v-for="option in (['EMPTY', 'PARTIAL', 'FURNISHED'] as const)" :key="option" type="button"
            :aria-pressed="form.furnishing === option" @click="form.furnishing = option"
          >
            {{ t(`furnishing.${option}`) }}
          </button>
        </div>
        <em class="help">{{ t('quoteForm.step2.furnishingHelp') }}</em>
        <em v-if="errors.furnishing" class="err">{{ errors.furnishing }}</em>
      </fieldset>

      <fieldset class="field">
        <legend class="q">{{ t('quoteForm.step2.doors') }}</legend>
        <div class="segmented" data-group="doors">
          <button
            type="button" :aria-pressed="form.doorsPainted === true"
            @click="form.doorsPainted = true"
          >
            {{ t('quoteForm.step2.doorsYes') }}
          </button>
          <button
            type="button" :aria-pressed="form.doorsPainted === false"
            @click="form.doorsPainted = false"
          >
            {{ t('quoteForm.step2.doorsNo') }}
          </button>
        </div>
        <em v-if="errors.doorsPainted" class="err">{{ errors.doorsPainted }}</em>
      </fieldset>

      <!-- Only once the answer is yes: a count nobody needs is a question nobody should be asked. -->
      <template v-if="form.doorsPainted">
        <label class="field">
          <span class="q">{{ t('quoteForm.step2.doorCount') }}</span>
          <span class="with-unit">
            <input v-model="form.doorCount" name="doorCount" inputmode="numeric" type="text">
            <i>adet</i>
          </span>
          <em class="help">{{ t('quoteForm.step2.doorCountHelp') }}</em>
          <em v-if="errors.doorCount" class="err">{{ errors.doorCount }}</em>
        </label>

        <fieldset class="field">
          <legend class="q">{{ t('quoteForm.step2.colourChange') }}</legend>
          <div class="segmented" data-group="colourChange">
            <button
              type="button" :aria-pressed="form.doorColourChange === true"
              @click="form.doorColourChange = true"
            >
              {{ t('quoteForm.step2.yes') }}
            </button>
            <button
              type="button" :aria-pressed="form.doorColourChange === false"
              @click="form.doorColourChange = false"
            >
              {{ t('quoteForm.step2.no') }}
            </button>
          </div>
          <em class="help">{{ t('quoteForm.step2.colourChangeHelp') }}</em>
          <em v-if="errors.doorColourChange" class="err">{{ errors.doorColourChange }}</em>
        </fieldset>
      </template>
    </section>

    <!-- ------------------------------------------------------------------ 3 · §1.3 -->
    <section v-if="step === 3" class="panel">
      <h1>{{ t('quoteForm.step3.title') }}</h1>

      <!-- One question, four visible answers. §1.3: nobody is asked whether they need alçı or macun —
           most people cannot tell, and the guess lands on the optimistic side. "Emin değilim" is a real
           option and will probably be the most chosen one. -->
      <fieldset class="field">
        <legend class="q">{{ t('quoteForm.step3.question') }}</legend>
        <em class="help">{{ t('quoteForm.step3.help') }}</em>
        <div class="options" data-group="wallCondition">
          <button
            v-for="option in (['GOOD', 'MINOR', 'MAJOR', 'UNSURE'] as const)" :key="option"
            type="button" :aria-pressed="form.wallCondition === option"
            @click="form.wallCondition = option"
          >
            <strong>{{ t(`quoteForm.step3.${option}`) }}</strong>
            <span>{{ t(`quoteForm.step3.${option}Note`) }}</span>
          </button>
        </div>
        <em v-if="errors.wallCondition" class="err">{{ errors.wallCondition }}</em>
      </fieldset>
    </section>

    <p v-if="failure" class="banner danger" role="alert">{{ failure }}</p>

    <div class="step-actions">
      <button v-if="step > 1" class="btn" type="button" :disabled="busy" @click="back">
        {{ t('quoteForm.back') }}
      </button>
      <button class="btn primary" type="button" :disabled="busy" @click="advance">
        {{ busy ? t('quoteForm.saving') : step === STEPS ? t('quoteForm.submit') : t('quoteForm.next') }}
      </button>
    </div>
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

.progress {
  margin: 0 0 var(--gap);
  font-size: 0.75rem;
  font-weight: 650;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ink-3);
}

.bar {
  height: 3px;
  border-radius: 999px;
  background: var(--surface-sunken);
  overflow: hidden;
}

.bar span {
  display: block;
  height: 100%;
  background: var(--brand);
  transition: width 0.25s ease;
}

.panel {
  display: grid;
  gap: var(--gap-section);
  padding: var(--gap-section);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

h1 {
  margin: 0;
  font-size: 1.25rem;
  letter-spacing: -0.01em;
}

.field {
  display: grid;
  gap: var(--gap);
  margin: 0;
  padding: 0;
  border: 0;
}

/* The question, at the size a question deserves — the answer's own text is not competing with it. */
.q {
  display: block;
  padding: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--ink);
}

.help {
  font-size: 0.85rem;
  font-style: normal;
  color: var(--ink-3);
}

.err {
  font-size: 0.85rem;
  font-style: normal;
  color: var(--danger);
}

select,
input {
  min-height: 3rem;
  padding: 0 0.7rem;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: inherit;
  /* Never below 16px: iOS zooms the page in on a focused input under it. */
  font: inherit;
  font-size: 1rem;
}

select:focus,
input:focus {
  outline: 2px solid var(--focus);
  outline-offset: 1px;
}

.with-unit {
  display: flex;
  align-items: center;
  gap: var(--gap);
}

.with-unit input {
  flex: 1 1 auto;
  text-align: right;
}

.with-unit i {
  font-style: normal;
  color: var(--ink-3);
}

.segmented {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: 1fr;
  gap: 2px;
  padding: 2px;
  background: var(--surface-sunken);
  border-radius: var(--radius-sm);
}

.segmented button,
.chips button {
  min-height: 3rem;
  padding: 0 0.6rem;
  border: 1px solid transparent;
  border-radius: calc(var(--radius-sm) - 1px);
  background: transparent;
  color: var(--ink-2);
  font: inherit;
  font-weight: 550;
  cursor: pointer;
}

.segmented button[aria-pressed='true'],
.chips button[aria-pressed='true'] {
  background: var(--brand-soft);
  border-color: var(--brand);
  color: var(--brand);
  font-weight: 650;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gap);
}

.chips button {
  min-height: 2.6rem;
  border-color: var(--line-strong);
  border-radius: 999px;
  background: var(--surface-2);
}

.chips em {
  font-style: normal;
  color: var(--ink-3);
}

.derived {
  margin: 0;
  padding: var(--gap-loose);
  border-left: 2px solid var(--brand);
  background: var(--surface-2);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
  font-size: 0.9rem;
  color: var(--ink-2);
}

.derived span {
  display: block;
  margin-top: var(--gap-tight);
  color: var(--ink);
}

.areas {
  display: grid;
  gap: var(--gap);
}

/* One per line, because each carries a sentence of explanation — a segmented control would clip it. */
.options {
  display: grid;
  gap: var(--gap);
}

.options button {
  display: grid;
  gap: 0.15rem;
  padding: var(--gap-loose);
  text-align: left;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  cursor: pointer;
}

.options button[aria-pressed='true'] {
  border-color: var(--brand);
  background: var(--brand-soft);
}

.options strong {
  font-size: 1rem;
  font-weight: 650;
}

.options span {
  font-size: 0.85rem;
  color: var(--ink-2);
}

.banner.danger {
  margin: 0;
  padding: var(--gap-loose);
  border-radius: var(--radius-sm);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 0.9rem;
}

.step-actions {
  display: flex;
  gap: var(--gap-loose);
}

.btn {
  min-height: 3rem;
  padding: 0 1.3rem;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 1rem;
  font-weight: 650;
  cursor: pointer;
}

.btn.primary {
  flex: 1 1 auto;
  border-color: var(--brand);
  background: var(--brand);
  color: var(--brand-ink);
}

.btn.primary:hover:not(:disabled) {
  background: var(--brand-hover);
}

.btn:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
