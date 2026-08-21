<script setup lang="ts">
/**
 * Stage 1's result (workflow §1.5, BOYA-32).
 *
 * §1.5 calls this the biggest loss point in the whole process, and gives the reason: a customer who sees
 * a range and leaves has given no phone number, so there is no way to follow up. Everything on the
 * screen is aimed at the two minutes after the number appears.
 *
 * The width of the range is not apologised for. §1.5 is explicit — "aralığın geniş olması kusur değil,
 * dürüsttür" — and the request to narrow it is what sells stage 2. So the screen says how wide it is,
 * why, in terms of what the customer answered, and what would narrow it.
 *
 * Two calls, in order: the draft for the answers (a screen that has to survive a reload cannot hold them
 * in memory), then the estimate for the range.
 */
import { DISTRICTS } from '~/utils/districts'

const { t } = useI18n()
const api = useApi()
const route = useRoute()

useHead({ title: t('meta.titleTemplate', { page: t('quoteResult.eyebrow') }) })

const id = String(route.query.talep ?? '')

const { data: draft, error: draftError } = await useAsyncData(`draft-${id}`, async () => {
  const { data, response } = await api.GET('/api/quote-requests/{id}',
    { params: { path: { id } } })
  if (!response.ok) {
    throw new Error('draft')
  }
  return data ?? null
})

/** Only asked once the answers are complete: the endpoint refuses otherwise, and rightly (BOYA-29). */
const { data: estimate, error: estimateError } = await useAsyncData(`estimate-${id}`, async () => {
  if (!draft.value?.priceable) {
    return null
  }
  const { data, response } = await api.POST('/api/quote-requests/{id}/estimate',
    { params: { path: { id } } })
  if (!response.ok) {
    throw new Error('estimate')
  }
  return data ?? null
}, { watch: [draft] })

/**
 * Why the band is as wide as it is, in the customer's own answers.
 *
 * The ratio is the server's — §5.9 owns that arithmetic — and these sentences name the inputs that
 * widened it. A percentage on its own explains nothing to the person reading it, and §1.5 requires the
 * explanation, not the number.
 */
const reasons = computed(() => {
  const said: string[] = [t('quoteResult.whyBase')]
  if (draft.value?.wallCondition === 'UNSURE') {
    said.push(t('quoteResult.whyUnsure'))
  }
  if (estimate.value?.areaWasGross) {
    said.push(t('quoteResult.whyGross'))
  }
  return said
})

const bandPercent = computed(() =>
  estimate.value ? (Number(estimate.value.bandRatio) * 100).toFixed(0) : '')

/**
 * The district's display name from its code. The build-time list rather than another API call: the
 * answer is already on the screen the customer came from, and one more round trip on the slowest screen
 * in the flow buys nothing.
 */
function districtName(code?: string | null): string {
  return DISTRICTS.find(district => district.code === code)?.name ?? code ?? ''
}

const doors = computed(() => {
  const count = draft.value?.doorCount
  if (!count) {
    return t('quoteResult.doorsNone')
  }
  return draft.value?.doorColourChange
    ? t('quoteResult.doorsColour', { count })
    : t('quoteResult.doorsCount', { count })
})
</script>

<template>
  <main>
    <!-- A draft that cannot be read at all: without this the screen says "Hesaplanıyor…" for ever,
         which is the failure mode that looks most like a working page. -->
    <template v-if="draftError">
      <section class="panel">
        <p>{{ t('quoteResult.failed') }}</p>
        <NuxtLink class="btn" to="/teklif-al">{{ t('quoteResult.goToForm') }}</NuxtLink>
      </section>
    </template>

    <template v-else-if="draft && !draft.priceable">
      <section class="panel">
        <p>{{ t('quoteResult.incomplete') }}</p>
        <NuxtLink class="btn primary" to="/teklif-al">{{ t('quoteResult.goToForm') }}</NuxtLink>
      </section>
    </template>

    <template v-else-if="estimateError">
      <section class="panel">
        <p>{{ t('quoteResult.failed') }}</p>
        <NuxtLink class="btn" to="/teklif-al">{{ t('quoteResult.goToForm') }}</NuxtLink>
      </section>
    </template>

    <template v-else-if="estimate">
      <!-- The range first and at the size of the answer it is. -->
      <section class="answer">
        <p class="eyebrow">{{ t('quoteResult.eyebrow') }}</p>
        <p class="range num">
          {{ formatPriceRange(Number(estimate.low), Number(estimate.high)) }}
        </p>
        <p class="vat">{{ t('quoteResult.vatNote') }}</p>
      </section>

      <section class="panel">
        <h2>{{ t('quoteResult.whyTitle') }} <span class="width">±%{{ bandPercent }}</span></h2>
        <ul class="reasons">
          <li v-for="reason in reasons" :key="reason">{{ reason }}</li>
        </ul>
        <p class="narrow">{{ t('quoteResult.whyNarrow') }}</p>
      </section>

      <section class="panel summary">
        <h2>{{ t('quoteResult.summaryTitle') }}</h2>
        <dl>
          <dt>{{ t('quoteResult.district') }}</dt>
          <dd>{{ districtName(draft!.districtCode) }}</dd>

          <dt>{{ t('quoteResult.area') }}</dt>
          <dd>
            {{ estimate.areaWasGross
              ? t('quoteResult.areaGross', {
                area: formatDecimal(Number(draft!.area)),
                net: formatDecimal(Number(estimate.netArea)),
              })
              : t('quoteResult.areaNet', { area: formatDecimal(Number(draft!.area)) }) }}
          </dd>

          <dt>{{ t('quoteResult.layout') }}</dt>
          <dd>{{ t(`layout.${draft!.layout}`) }}</dd>

          <dt>{{ t('quoteResult.scope') }}</dt>
          <dd>{{ t(`scope.${draft!.scope}`) }}</dd>

          <dt>{{ t('quoteResult.furnishing') }}</dt>
          <dd>{{ t(`furnishing.${draft!.furnishing}`) }}</dd>

          <dt>{{ t('quoteResult.doors') }}</dt>
          <dd>{{ doors }}</dd>

          <dt>{{ t('quoteResult.walls') }}</dt>
          <dd>{{ t(`wallConditionShort.${draft!.wallCondition}`) }}</dd>
        </dl>

        <h3>{{ t('quoteResult.areasTitle') }}</h3>
        <p class="areas">{{ estimate.rooms.map(room => room.label).join(' · ') }}</p>
        <p class="hint">{{ t('quoteResult.photoCount', { count: estimate.photoCount }) }}</p>
      </section>

      <div class="actions">
        <NuxtLink class="btn primary" to="/cekim">{{ t('quoteResult.continue') }}</NuxtLink>
        <NuxtLink class="quiet" to="/">{{ t('quoteResult.leave') }}</NuxtLink>
      </div>
      <p class="hint continue-note">{{ t('quoteResult.continueNote') }}</p>
      <NuxtLink class="quiet edit" to="/teklif-al">{{ t('quoteResult.edit') }}</NuxtLink>
    </template>

    <p v-else class="panel">{{ t('quoteResult.loading') }}</p>
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

.answer {
  padding: var(--gap-section) 0 0;
}

.eyebrow {
  margin: 0 0 var(--gap);
  font-size: 0.75rem
;
  font-weight: 650;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--brand);
}

.range {
  margin: 0;
  font-family: var(--mono);
  /* Fluid: the range is two five-digit figures and a dash, and on a narrow phone a fixed size wraps it
     into something that reads as two prices rather than one range. */
  font-size: clamp(1.5rem, 6.5vw, 2.2rem);
  font-variant-numeric: tabular-nums;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.vat {
  margin: var(--gap-tight) 0 0;
  font-size: 0.85rem;
  color: var(--ink-3);
}

.panel {
  padding: var(--gap-section);
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

h2 {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--gap);
  margin: 0 0 var(--gap-loose);
  font-size: 1.05rem;
}

/* The width, next to the question about it — not hidden in the prose. */
.width {
  font-family: var(--mono);
  font-size: 0.9rem;
  font-weight: 650;
  color: var(--brand);
}

.reasons {
  margin: 0;
  padding-left: 1.1rem;
  display: grid;
  gap: var(--gap);
  font-size: 0.95rem;
  color: var(--ink-2);
}

.narrow {
  margin: var(--gap-section) 0 0;
  padding-top: var(--gap-loose);
  border-top: 1px solid var(--line);
  font-size: 0.95rem;
  color: var(--ink);
}

.summary dl {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: var(--gap) var(--gap-section);
  margin: 0;
  font-size: 0.95rem;
}

.summary dt {
  color: var(--ink-3);
}

.summary dd {
  margin: 0;
  text-align: right;
}

h3 {
  margin: var(--gap-section) 0 var(--gap);
  font-size: 0.85rem;
  font-weight: 650;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ink-3);
}

.areas {
  margin: 0;
  font-size: 0.95rem;
}

.hint {
  margin: var(--gap) 0 0;
  font-size: 0.85rem;
  color: var(--ink-3);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--gap-loose) var(--gap-section);
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
  font-size: 1.05rem;
  font-weight: 650;
  text-decoration: none;
}

.btn.primary {
  border-color: var(--brand);
  background: var(--brand);
  color: var(--brand-ink);
}

.btn.primary:hover {
  background: var(--brand-hover);
}

.quiet {
  color: var(--ink-2);
  font-weight: 550;
  text-decoration: none;
  border-bottom: 1px solid var(--line-strong);
}

.continue-note {
  margin: 0;
}

.edit {
  justify-self: start;
  font-size: 0.9rem;
}
</style>
