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

/**
 * §1.5's third option, and the one it calls far more important than it looks: the customer who sees a
 * range and leaves has given no number, so this is the last point at which they can be reached at all.
 *
 * The field appears only when the offer is taken. A phone input sitting on the screen next to the price
 * reads as the catch, on the one screen where there is no catch.
 */
const smsOpen = ref(false)
const phone = ref('')
const smsBusy = ref(false)
const smsDone = ref(false)
const smsError = ref('')

/** The same rule the server applies, so the answer is immediate; the server still decides. */
const TURKISH_MOBILE = /^(?:\+?90|0)?5\d{9}$/

async function sendSms() {
  if (smsBusy.value) {
    return
  }
  smsError.value = ''
  if (!TURKISH_MOBILE.test(phone.value.replace(/[^0-9+]/g, ''))) {
    smsError.value = t('quoteResult.smsInvalid')
    return
  }
  smsBusy.value = true
  try {
    const { response } = await api.POST('/api/quote-requests/{id}/estimate-sms', {
      params: { path: { id } },
      body: { phone: phone.value },
    })
    if (!response.ok) {
      smsError.value = t('quoteResult.smsFailed')
      return
    }
    smsDone.value = true
    smsOpen.value = false
  }
  catch {
    smsError.value = t('quoteResult.smsFailed')
  }
  finally {
    smsBusy.value = false
  }
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

      <!-- What can be done now, in descending weight: go on, change something, stop. Editing is an
           action rather than a line of text inside a card — showing somebody a summary and then making
           the way to correct it the faintest thing on the screen is an invitation nobody accepts. -->
      <div class="actions">
        <NuxtLink class="btn primary" to="/cekim">{{ t('quoteResult.continue') }}</NuxtLink>
        <NuxtLink class="btn outline edit" :to="`/teklif-al?talep=${id}`">
          {{ t('quoteResult.edit') }}
        </NuxtLink>
        <NuxtLink class="quiet" to="/">{{ t('quoteResult.leave') }}</NuxtLink>
      </div>
      <p class="hint continue-note">{{ t('quoteResult.continueNote') }}</p>

      <!-- §1.5: "bu aşamada numara bırakan müşteriye sonradan dönülebilir — göründüğünden çok daha
           önemli". So it is a panel of its own with the reason on it, not a link under the fold: the
           customer about to leave is the one this is for, and they are not reading carefully. It stays
           an outline button so it does not compete with "Kesin fiyat al" — second, not quiet. -->
      <section class="panel sms" :data-open="smsOpen || smsDone">
        <template v-if="smsDone">
          <p class="sms-done" role="status">{{ t('quoteResult.smsDone') }}</p>
        </template>

        <template v-else>
          <h2>{{ t('quoteResult.smsOffer') }}</h2>
          <!-- Before the click, not after: the reason is what makes somebody click. -->
          <p class="hint">{{ t('quoteResult.smsWhy') }}</p>

          <button v-if="!smsOpen" class="sms-offer btn outline" type="button" @click="smsOpen = true">
            {{ t('quoteResult.smsButton') }}
          </button>

          <div v-else class="sms-form">
            <label>
              <span class="q">{{ t('quoteResult.smsPhone') }}</span>
              <input
                v-model="phone" name="phone" type="tel" inputmode="tel" autocomplete="tel"
                :placeholder="t('quoteResult.smsPlaceholder')"
              >
            </label>
            <p v-if="smsError" class="err">{{ smsError }}</p>
            <div class="sms-actions">
              <button class="btn primary" type="button" :disabled="smsBusy" @click="sendSms">
                {{ smsBusy ? t('quoteResult.smsSending') : t('quoteResult.smsSubmit') }}
              </button>
              <button class="quiet" type="button" @click="smsOpen = false">
                {{ t('quoteResult.smsCancel') }}
              </button>
            </div>
          </div>
        </template>
      </section>

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

/* Its own card, with the brand edge the form uses for a derived answer. Enough presence to be seen by
   somebody who has already decided to leave, and not enough to argue with the primary action. */
.sms {
  display: grid;
  gap: var(--gap);
  border-left: 3px solid var(--brand);
}

.sms h2 {
  display: block;
  margin: 0;
  font-size: 1.05rem;
}

.sms .hint {
  margin: 0;
}

/* Outline, not filled: second in the hierarchy rather than absent from it. */
.btn.outline {
  justify-self: start;
  margin-top: var(--gap-tight);
  border-color: var(--brand);
  background: var(--surface);
  color: var(--brand);
  cursor: pointer;
}

.btn.outline:hover {
  background: var(--brand-soft);
}

.sms-form {
  display: grid;
  gap: var(--gap-loose);
  margin-top: var(--gap);
}

.sms-form label {
  display: grid;
  gap: var(--gap);
}

.sms-form .q {
  font-size: 0.95rem;
  font-weight: 600;
}

.sms-form input {
  min-height: 3rem;
  padding: 0 0.7rem;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: inherit;
  /* 16px floor: iOS zooms the page in on anything smaller. */
  font: inherit;
  font-size: 1rem;
}

.sms-actions {
  display: flex;
  align-items: center;
  gap: var(--gap-section);
}

.sms-actions .btn {
  border: 1px solid var(--brand);
  cursor: pointer;
}

.sms-actions .quiet {
  border: 0;
  border-bottom: 1px solid var(--line-strong);
  background: none;
  font: inherit;
  cursor: pointer;
}

.sms-done {
  margin: 0;
  color: var(--live);
  font-size: 0.95rem;
  font-weight: 550;
}

.err {
  margin: 0;
  font-size: 0.9rem;
  color: var(--danger);
}

/* Outline: second in the row, not absent from it. The filled button is still the one thing the page
   is asking for. */
.actions .edit {
  margin-top: 0;
}
</style>
