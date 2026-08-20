<script setup lang="ts">
/**
 * The internal tool (workflow §12, increment 1): price a job by hand and read the breakdown.
 *
 * This screen is what makes increment 1 shippable. The riskiest assumption in the system is whether the
 * engine's figures are figures this business would charge, and the cheapest way to find out is for the
 * business to enter a job whose price it already knows — no website, no photographs, no customer.
 *
 * So the answer leads with the two numbers that can be compared (the band and the total), and every
 * assumption behind them is on the same screen: the areas the layout implied, the net area used, and the
 * quantity behind each line. A figure nobody can take apart is a figure nobody will argue with, and
 * arguing with it is the entire exercise.
 */
import type { Schemas } from '@decorating/api-client'

const { t } = useI18n()
const api = useApi()

useHead({ title: t('meta.titleTemplate', { page: t('pages.calculate.title') }) })

// Straight from the generated contract: a renamed field or a dropped enum value is a compile error
// here rather than a form that posts something the API will not accept.
type Request = Schemas['CalculateQuoteRequest']
type Result = Schemas['QuoteCalculationResponse']

const form = reactive({
  districtCode: 'KADIKOY',
  area: '92',
  areaBasis: 'NET' as Request['areaBasis'],
  layout: 'THREE_PLUS_ONE' as Request['layout'],
  scope: 'WHOLE_HOME' as Request['scope'],
  selectedRooms: [] as NonNullable<Request['selectedRooms']>,
  wallCondition: 'MINOR' as Request['wallCondition'],
  furnishing: 'FURNISHED' as Request['furnishing'],
  doorCount: '8',
  doorColourChange: true,
  doorCountEstimated: false,
  noElevator: false,
  rush: false,
})

const LAYOUTS = ['STUDIO', 'ONE_PLUS_ONE', 'TWO_PLUS_ONE', 'THREE_PLUS_ONE', 'FOUR_PLUS_ONE',
  'FIVE_PLUS_ONE'] as const
const CONDITIONS = ['GOOD', 'MINOR', 'MAJOR', 'UNSURE'] as const
const FURNISHINGS = ['EMPTY', 'PARTIAL', 'FURNISHED'] as const

/** The four things that add cost or uncertainty, as one row of on/off chips. */
const EXTRAS = ['doorColourChange', 'doorCountEstimated', 'noElevator', 'rush'] as const
/**
 * The areas this layout actually has, with their counts. Offering all eight room types instead was the
 * thing that made this control unanswerable: a study checkbox in a 3+1 did nothing at all, and
 * "Yatak odası" gave no clue that it meant two rooms.
 */
const areas = computed(() => areasFor(form.layout as LayoutCode))

const selectedAreaCount = computed(() => areaCount(form.layout as LayoutCode, form.selectedRooms))

const wholeHomeCount = computed(() => totalAreaCount(form.layout as LayoutCode))

function toggleArea(type: Request['selectedRooms'] extends (infer T)[] | undefined ? T : never) {
  const index = form.selectedRooms.indexOf(type)
  if (index === -1) {
    form.selectedRooms.push(type)
  }
  else {
    form.selectedRooms.splice(index, 1)
  }
}

// A selection the new layout cannot hold would sit there ticked and price nothing, which is exactly
// how the old control misled: dropping it is louder than honouring it silently.
watch(() => form.layout, () => {
  form.selectedRooms = form.selectedRooms.filter(type =>
    areas.value.some(area => area.type === type))
})

const result = ref<Result | null>(null)
const failure = ref('')
const busy = ref(false)

const areaValid = computed(() => isDecimal(form.area) && parseDecimal(form.area) >= 1)
const doorsValid = computed(() => /^\d+$/.test(form.doorCount))
const roomsValid = computed(() => form.scope === 'WHOLE_HOME' || form.selectedRooms.length > 0)
const canSubmit = computed(() => areaValid.value && doorsValid.value && roomsValid.value)

async function calculate() {
  if (!canSubmit.value) {
    return
  }
  busy.value = true
  failure.value = ''

  const { data, error, response } = await api.POST('/api/op/price-calculations', {
    body: {
      districtCode: form.districtCode,
      area: parseDecimal(form.area),
      areaBasis: form.areaBasis,
      layout: form.layout,
      scope: form.scope,
      selectedRooms: form.scope === 'SELECTED_ROOMS' ? form.selectedRooms : undefined,
      wallCondition: form.wallCondition,
      furnishing: form.furnishing,
      doorCount: Number(form.doorCount),
      doorColourChange: form.doorColourChange,
      doorCountEstimated: form.doorCountEstimated,
      // The form asks what the job *has*, and every one of those makes it cost more or widen the
      // band. The engine's flag is the other way round, so it is flipped here rather than asking the
      // operator to think in negatives.
      hasElevator: !form.noElevator,
      rush: form.rush,
    },
  })
  busy.value = false

  if (!response.ok || !data) {
    failure.value = error?.detail ?? (response.status === 401 || response.status === 403
      ? 'Operatör girişi gerekiyor.'
      : `Hesaplanamadı (HTTP ${response.status}).`)
    result.value = null
    return
  }
  result.value = data
}

/** The margin the figures imply, so the operator does not have to divide two numbers on screen. */
const marginRatio = computed(() => {
  const quote = result.value
  if (!quote || Number(quote.totalCost) === 0) {
    return null
  }
  return (Number(quote.subtotalExVat) - Number(quote.totalCost)) / Number(quote.totalCost)
})

const PERCENT = new Intl.NumberFormat('tr-TR', { style: 'percent', maximumFractionDigits: 1 })

/*
 * After the first calculation the tool recalculates as the form changes, which is what turns it from a
 * form into a calculator: the operator nudges the m² and watches the figure move. Debounced, and never
 * before the first explicit press — nobody wants a request fired at them while they are still typing
 * the first number.
 */
let pending: ReturnType<typeof setTimeout> | undefined

watch(() => JSON.stringify(form), () => {
  if (!result.value || !canSubmit.value) {
    return
  }
  clearTimeout(pending)
  pending = setTimeout(() => calculate(), 400)
})
</script>

<template>
  <div class="screen">
    <header class="bar">
      <div class="bar-inner">
        <p class="eyebrow">Operatör paneli</p>
        <h1>{{ t('pages.calculate.title') }}</h1>
      </div>
    </header>

    <main class="content">
      <p class="intro">{{ t('calculate.intro') }}</p>
      <p v-if="failure" class="banner danger" role="alert">{{ failure }}</p>

      <div class="layout">
      <form class="panel" @submit.prevent="calculate()">
        <div class="grid">
          <label class="field-row">
            <span>{{ t('calculate.district') }}</span>
            <select v-model="form.districtCode">
              <option v-for="district in DISTRICTS" :key="district.code" :value="district.code">
                {{ district.name }}
              </option>
            </select>
          </label>

          <label class="field-row">
            <span>{{ t('calculate.layout') }}</span>
            <select v-model="form.layout">
              <option v-for="layout in LAYOUTS" :key="layout" :value="layout">
                {{ t(`layout.${layout}`) }}
              </option>
            </select>
          </label>

          <div class="field-row">
            <span>{{ t('calculate.area') }}</span>
            <div class="area">
              <span class="field" :data-invalid="!areaValid">
                <input v-model="form.area" class="num" inputmode="decimal" type="text">
                <i>m²</i>
              </span>
              <div class="segmented small">
                <button
                  v-for="basis in (['NET', 'GROSS'] as const)" :key="basis" type="button"
                  :aria-pressed="form.areaBasis === basis" @click="form.areaBasis = basis"
                >
                  {{ t(`calculate.areaBasis.${basis}`) }}
                </button>
              </div>
            </div>
          </div>

          <div class="field-row">
            <span>{{ t('calculate.doorCount') }}</span>
            <div class="area">
              <span class="field" :data-invalid="!doorsValid">
                <input v-model="form.doorCount" class="num" inputmode="numeric" type="text">
                <i>adet</i>
              </span>
            </div>
          </div>
        </div>

        <fieldset>
          <legend>{{ t('calculate.scope') }}</legend>
          <div class="segmented">
            <button
              v-for="scope in (['WHOLE_HOME', 'SELECTED_ROOMS'] as const)" :key="scope" type="button"
              :aria-pressed="form.scope === scope" @click="form.scope = scope"
            >
              {{ t(`scope.${scope}`) }}
            </button>
          </div>

          <!-- "Tüm ev" is four rooms to the person typing and seven areas to the engine, so the screen
               says which seven before anything is priced. -->
          <p v-if="form.scope === 'WHOLE_HOME'" class="scope">
            {{ t('calculate.scopeWholeHome', {
              layout: t(`layout.${form.layout}`), count: wholeHomeCount }) }}
            <span class="areas">
              <span v-for="area in areas" :key="area.type">
                {{ t(`rooms.${area.type}`) }}<i v-if="area.count > 1"> ×{{ area.count }}</i>
              </span>
            </span>
          </p>

          <template v-else>
            <div class="chips" data-group="areas" role="group" :aria-label="t('calculate.selectRooms')">
              <button
                v-for="area in areas"
                :key="area.type"
                type="button"
                :aria-pressed="form.selectedRooms.includes(area.type)"
                @click="toggleArea(area.type)"
              >
                {{ t(`rooms.${area.type}`) }}<i v-if="area.count > 1"> ×{{ area.count }}</i>
              </button>
            </div>
            <p class="scope">
              <strong>{{ t('calculate.scopeSelected', { count: selectedAreaCount }) }}</strong>
              — {{ t('calculate.scopeHint') }}
              <button
                v-if="selectedAreaCount < wholeHomeCount" class="link" type="button"
                @click="form.selectedRooms = areas.map(area => area.type)"
              >
                {{ t('calculate.selectAll') }}
              </button>
              <button
                v-if="form.selectedRooms.length" class="link" type="button"
                @click="form.selectedRooms = []"
              >
                {{ t('calculate.clearSelection') }}
              </button>
            </p>
            <p v-if="!roomsValid" class="field-error">{{ t('calculate.noAreas') }}</p>
          </template>
        </fieldset>

        <!-- Two choices about the home, side by side: three stacked full-width bars was three times
             the furniture for one question each. The labels are the operator's short forms; the long
             customer phrasings belong in the customer's own form, where they are questions. -->
        <div class="choices">
          <fieldset>
            <legend>{{ t('calculate.wallCondition') }}</legend>
            <div class="segmented" data-cols="4">
              <button
                v-for="condition in CONDITIONS" :key="condition" type="button"
                :aria-pressed="form.wallCondition === condition"
                :title="t(`wallCondition.${condition}`)"
                @click="form.wallCondition = condition"
              >
                {{ t(`wallConditionShort.${condition}`) }}
              </button>
            </div>
          </fieldset>

          <fieldset>
            <legend>{{ t('calculate.furnishing') }}</legend>
            <div class="segmented">
              <button
                v-for="furnishing in FURNISHINGS" :key="furnishing" type="button"
                :aria-pressed="form.furnishing === furnishing" @click="form.furnishing = furnishing"
              >
                {{ t(`furnishing.${furnishing}`) }}
              </button>
            </div>
          </fieldset>
        </div>

        <!-- One row, one label, one meaning: everything switched on here makes the job dearer or the
             band wider. Four checkboxes in a three-then-one grid said none of that. -->
        <fieldset>
          <legend>{{ t('calculate.extras') }}</legend>
          <div class="chips" data-group="extras" role="group" :aria-label="t('calculate.extras')">
            <button
              v-for="extra in EXTRAS"
              :key="extra"
              type="button"
              :aria-pressed="form[extra]"
              @click="form[extra] = !form[extra]"
            >
              {{ t(`calculate.extraLabels.${extra}`) }}
            </button>
          </div>
          <p class="scope">{{ t('calculate.extrasHint') }}</p>
        </fieldset>

        <button class="btn primary wide" type="submit" :disabled="busy || !canSubmit">
          {{ busy ? '…' : result ? t('calculate.recalculate') : t('calculate.submit') }}
        </button>
      </form>

      <!-- The answer stays in view while the inputs are nudged: this is a comparison tool, and the
           figure being compared should not scroll away from the field being changed. -->
      <aside class="answer-pane">
      <template v-if="result">
        <section class="panel answer">
          <p class="answer-label">{{ t('calculate.result.band') }}</p>
          <p class="band num">
            {{ formatPriceRange(Number(result.bandLow), Number(result.bandHigh)) }}
          </p>
          <p class="answer-total">
            {{ t('calculate.result.total') }}
            <strong class="num">{{ formatAmount(Number(result.total)) }}</strong>
          </p>
          <p class="hint">
            {{ t('calculate.result.bandWhy', { ratio: (Number(result.bandRatio) * 100).toFixed(0) }) }}
          </p>

          <dl class="figures">
            <dt>{{ t('calculate.result.cost') }}</dt>
            <dd class="num">{{ formatAmount(Number(result.totalCost)) }}</dd>
            <dt>{{ t('calculate.result.margin') }}</dt>
            <dd class="num">{{ marginRatio === null ? '—' : PERCENT.format(marginRatio) }}</dd>
            <dt>{{ t('calculate.result.subtotal') }}</dt>
            <dd class="num">{{ formatAmount(Number(result.subtotalExVat)) }}</dd>
            <dt>{{ t('calculate.result.vat') }}</dt>
            <dd class="num">{{ formatAmount(Number(result.vatAmount)) }}</dd>
            <dt>{{ t('calculate.result.days') }}</dt>
            <dd class="num">{{ result.billableDays }}</dd>
          </dl>
          <p v-if="result.minimumBinding" class="banner note">
            {{ t('calculate.result.minimumBinding') }}: {{ formatAmount(Number(result.minimumCost)) }}
          </p>
        </section>

        <section class="panel">
          <h2>{{ t('calculate.result.assumptions') }}</h2>
          <dl class="figures">
            <dt>{{ t('calculate.result.netArea') }}</dt>
            <dd class="num">
              {{ formatDecimal(Number(result.netArea)) }} m²
              <em v-if="result.areaWasGross">
                ({{ t('calculate.result.convertedFrom', { area: form.area }) }})
              </em>
            </dd>
            <dt>{{ t('calculate.result.areas') }}</dt>
            <dd class="num">{{ result.rooms.length }} · {{ result.photoCount }}</dd>
            <dt>{{ t('calculate.result.pricedWith') }}</dt>
            <dd class="num">{{ result.priceBookVersion }}</dd>
          </dl>
          <ul class="rooms">
            <li v-for="room in result.rooms" :key="room.label">{{ room.label }}</li>
          </ul>
        </section>

        <section class="panel">
          <h2>{{ t('calculate.result.lines') }}</h2>
          <ul class="lines">
            <li v-for="line in result.lines" :key="line.code">
              <span class="line-name">{{ t(`priceBook.codes.${line.code}`) }}</span>
              <span class="line-qty num">
                {{ formatDecimal(Number(line.quantity)) }} {{ t(`priceBook.units.${line.unit}`) }}
              </span>
              <span class="line-total num">{{ formatAmount(Number(line.lineTotal)) }}</span>
            </li>
          </ul>
        </section>
      </template>

      <p v-else class="waiting">{{ t('calculate.waiting') }}</p>
      </aside>
      </div>
    </main>
  </div>
</template>

<style scoped>
.screen {
  min-height: 100dvh;
  background: var(--bg);
}

.bar {
  position: sticky;
  top: 0;
  z-index: 2;
  background: color-mix(in srgb, var(--surface) 92%, transparent);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--line);
}

.bar-inner,
.content {
  max-width: 78rem;
  margin: 0 auto;
}

.bar-inner {
  padding: 0.75rem 1rem;
}

.content {
  padding: 1rem 1.25rem 4rem;
  display: grid;
  gap: var(--gap-loose);
}

/* A calculator, not a page of stacked panels: the form on the left, the answer beside it and staying
   there. Below 64rem there is no room for two columns, so it becomes form-then-answer. */
.layout {
  display: grid;
  gap: var(--gap-loose);
}

@media (min-width: 64rem) {
  .layout {
    grid-template-columns: minmax(0, 1.15fr) minmax(22rem, 0.85fr);
    align-items: start;
  }

  .answer-pane {
    position: sticky;
    top: 5.5rem;
  }
}

.answer-pane {
  display: grid;
  gap: var(--gap-loose);
  align-content: start;
}

.waiting {
  margin: 0;
  padding: 1.5rem 1.25rem;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius);
  color: var(--ink-3);
  font-size: 0.95rem;
  line-height: 1.6;
}

.eyebrow {
  margin: 0;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--ink-3);
}

h1 {
  margin: 0.1rem 0 0;
  font-size: 1.375rem;
  font-weight: 650;
  letter-spacing: -0.01em;
}

h2 {
  margin: 0 0 var(--gap-loose);
  font-size: 0.72rem;
  font-weight: 650;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--ink-3);
}

.intro,
.hint {
  margin: 0;
  font-size: 0.925rem;
  color: var(--ink-2);
}

.panel {
  padding: 1rem;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  display: grid;
  gap: var(--gap-loose);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12.5rem, 1fr));
  gap: var(--gap-loose);
}

.field-row {
  display: grid;
  gap: 0.25rem;
}

.field-row > span,
legend {
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--ink-2);
}

.area {
  display: flex;
  gap: var(--gap);
  align-items: center;
}

/* Wide enough for four digits: the field was clipping the leading "1" of 140 m². */
.area .field {
  flex: 1 1 5.5rem;
  min-width: 5rem;
}

.field {
  display: flex;
  align-items: center;
  flex: 1 1 auto;
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
}

.field[data-invalid='true'] {
  border-color: var(--danger);
}

.field:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand) 18%, transparent);
}

.field i {
  padding-right: 0.55rem;
  font-size: 0.75rem;
  font-style: normal;
  color: var(--ink-3);
}

input[type='text'] {
  flex: 1 1 auto;
  width: 100%;
  min-height: 2.6rem;
  padding: 0 0.5rem;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 1rem;
  text-align: right;
}

input[type='text']:focus {
  outline: none;
}

select {
  min-height: 2.6rem;
  padding: 0 0.5rem;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: inherit;
  font: inherit;
  font-size: 1rem;
}

fieldset {
  margin: 0;
  padding: 0;
  border: 0;
  display: grid;
  gap: var(--gap);
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

.segmented.small {
  flex: none;
  width: 7.75rem;
}

.segmented button {
  min-height: 2.5rem;
  padding: 0 0.45rem;
  white-space: nowrap;
  border: 0;
  border-radius: calc(var(--radius-sm) - 1px);
  background: transparent;
  color: var(--ink-2);
  font: inherit;
  font-size: 0.9rem;
  font-weight: 550;
  cursor: pointer;
}

.segmented button[aria-pressed='true'] {
  background: var(--surface);
  color: var(--ink);
  box-shadow: var(--shadow);
}

/* Toggle chips rather than checkboxes: this is a different kind of choice from the four flags below,
   and it should not look like them. */
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gap);
}

.chips button {
  min-height: 2.5rem;
  padding: 0 0.8rem;
  border: 1px solid var(--line-strong);
  border-radius: 999px;
  background: var(--surface);
  color: var(--ink-2);
  font: inherit;
  font-size: 0.925rem;
  font-weight: 550;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.chips button:hover {
  border-color: var(--brand);
}

.chips button[aria-pressed='true'] {
  background: var(--brand);
  border-color: var(--brand);
  color: var(--brand-ink);
}

.chips i,
.areas i {
  font-style: normal;
  opacity: 0.7;
}

.scope {
  margin: 0;
  font-size: 0.875rem;
  line-height: 1.55;
  color: var(--ink-2);
}

.scope strong {
  color: var(--ink);
}

.areas {
  display: block;
  margin-top: 0.15rem;
  color: var(--ink);
}

.areas > span:not(:last-child)::after {
  content: ' · ';
  color: var(--ink-3);
}

.link {
  margin-left: 0.4rem;
  padding: 0;
  border: 0;
  background: none;
  color: var(--brand);
  font: inherit;
  font-size: 0.85rem;
  text-decoration: underline;
  text-underline-offset: 2px;
  cursor: pointer;
}

.choices {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: var(--gap-loose);
}

/* Four segments need more room than three, so the split is not down the middle. */
@media (min-width: 48rem) {
  .choices {
    grid-template-columns: 1.45fr 1fr;
  }
}

/* On a phone the four conditions go two by two rather than clipping their own words. */
@media (max-width: 30rem) {
  .segmented[data-cols='4'] {
    grid-auto-flow: row;
    grid-template-columns: 1fr 1fr;
  }
}

.field-error {
  margin: 0;
  font-size: 0.8rem;
  color: var(--danger);
}

.btn {
  min-height: 2.85rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  font: inherit;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
}

.btn.primary {
  background: var(--brand);
  color: var(--brand-ink);
}

.btn.primary:hover {
  background: var(--brand-hover);
}

.btn.wide {
  width: 100%;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* The answer leads with the two figures the business can compare with its own. */
.answer {
  border-color: color-mix(in srgb, var(--brand) 35%, var(--line));
  background: color-mix(in srgb, var(--brand-soft) 45%, var(--surface));
  gap: var(--gap);
}

.answer-label {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 650;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--ink-3);
}

.band {
  margin: 0;
  font-size: clamp(1.45rem, 3vw, 1.9rem);
  font-weight: 650;
  letter-spacing: -0.02em;
}

.answer-total {
  margin: 0;
  font-size: 0.95rem;
  color: var(--ink-2);
}

.answer-total strong {
  color: var(--ink);
}

.figures {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.45rem 1rem;
  margin: 0;
  font-size: 0.95rem;
}

.figures dt {
  color: var(--ink-2);
}

.figures dd {
  margin: 0;
  font-weight: 550;
  text-align: right;
}

.figures em {
  font-style: normal;
  font-weight: 400;
  color: var(--ink-3);
}

.rooms {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gap-tight) var(--gap);
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 0.85rem;
  color: var(--ink-2);
}

.rooms li {
  padding: 0.1rem 0.45rem;
  background: var(--surface-2);
  border-radius: 999px;
}

.lines {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 1px;
  background: var(--line);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.lines li {
  display: grid;
  grid-template-columns: minmax(6rem, 1fr) auto auto;
  gap: var(--gap);
  align-items: baseline;
  padding: 0.6rem 0.75rem;
  background: var(--surface);
  font-size: 0.925rem;
}

.line-qty {
  color: var(--ink-2);
  font-size: 0.825rem;
}

.line-total {
  font-weight: 600;
  min-width: 6.5rem;
  text-align: right;
}
</style>
