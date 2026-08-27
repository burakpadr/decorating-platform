<script setup lang="ts">
/**
 * Stage 2's guidance screen: the three shooting rules and the data consent (§2.3, BOYA-39).
 *
 * §2.3 puts two unrelated things on one screen and it is right to: the rules are what stops a retake,
 * the notice is what makes the photographs lawful to hold, and both are things the customer needs
 * before the camera opens rather than after. §5's inventory budgets fifteen seconds for the whole
 * screen, which is the reason the rules are three short lines and not a page of advice.
 *
 * The notice text is fetched rather than translated. It is the artefact a grant refers to — the server
 * stamps `text_version` against the words it served — so holding a second copy here would let the two
 * drift and leave rows pointing at a version that no longer says what was agreed (decision 0018).
 *
 * A refusal is a real answer and is recorded as one. The customer keeps the range they already have;
 * what they lose is the photograph-based quote, and saying that plainly is better than a disabled
 * button with no explanation.
 */
const { t } = useI18n()
const api = useApi()
const route = useRoute()

useHead({ title: t('meta.titleTemplate', { page: t('pages.guide.title') }) })

const id = String(route.query.talep ?? '')

const { data: notice, error: noticeError, refresh } = await useAsyncData('consent-notice', async () => {
  const { data, response } = await api.GET('/api/consent-notices/{type}', {
    params: { path: { type: 'PROCESSING' } },
  })
  if (!response.ok) {
    throw new Error('notice')
  }
  return data ?? null
})

const blocks = computed(() => (notice.value ? noticeBlocks(notice.value.body) : []))

const agreed = ref(false)
const busy = ref(false)
const failed = ref(false)
const changed = ref(false)
const declined = ref(false)
const missing = ref(false)

const onward = computed(() => `/cekim/oda?talep=${encodeURIComponent(id)}`)
const backToResult = computed(() => `/teklif-al/sonuc?talep=${encodeURIComponent(id)}`)

async function decide(granted: boolean) {
  if (busy.value || !notice.value) {
    return
  }
  // Answering is the point of the screen, so an unticked box is a prompt rather than a dead button:
  // a disabled control here reads as a broken page and people leave instead of asking why.
  if (granted && !agreed.value) {
    missing.value = true
    return
  }

  busy.value = true
  failed.value = false
  changed.value = false
  missing.value = false
  try {
    const { response } = await api.POST('/api/quote-requests/{id}/consents', {
      params: { path: { id } },
      body: { type: 'PROCESSING', granted, textVersion: notice.value.textVersion },
    })
    if (response.status === 409) {
      // The words moved while this page was open. Re-read them rather than send the tick again: the
      // grant would otherwise name a version the customer never saw.
      changed.value = true
      agreed.value = false
      await refresh()
      return
    }
    if (!response.ok) {
      failed.value = true
      return
    }
    if (!granted) {
      declined.value = true
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
    <template v-if="!id">
      <p class="panel">{{ t('captureGuide.incomplete') }}</p>
      <NuxtLink class="btn outline" to="/teklif-al">{{ t('captureGuide.goToForm') }}</NuxtLink>
    </template>

    <template v-else-if="declined">
      <p class="eyebrow">{{ t('captureGuide.eyebrow') }}</p>
      <p class="panel">{{ t('captureGuide.declined') }}</p>
      <NuxtLink class="btn outline" :to="backToResult">{{ t('captureGuide.backToResult') }}</NuxtLink>
    </template>

    <template v-else>
      <p class="eyebrow">{{ t('captureGuide.eyebrow') }}</p>
      <h1>{{ t('captureGuide.title') }}</h1>
      <p class="intro">{{ t('captureGuide.intro') }}</p>

      <ol class="rules">
        <li class="rule">
          <h2>{{ t('captureGuide.ruleLightTitle') }}</h2>
          <p>{{ t('captureGuide.ruleLightBody') }}</p>
        </li>
        <li class="rule">
          <h2>{{ t('captureGuide.rulePeopleTitle') }}</h2>
          <p>{{ t('captureGuide.rulePeopleBody') }}</p>
        </li>
        <li class="rule">
          <h2>{{ t('captureGuide.ruleSteadyTitle') }}</h2>
          <p>{{ t('captureGuide.ruleSteadyBody') }}</p>
        </li>
      </ol>

      <section class="notice">
        <h2>{{ t('captureGuide.noticeTitle') }}</h2>

        <p v-if="noticeError" class="err" role="alert">{{ t('captureGuide.noticeFailed') }}</p>
        <template v-else-if="notice">
          <p v-if="changed" class="err" role="alert">{{ t('captureGuide.changed') }}</p>
          <!-- Rendered as text, never v-html: see app/utils/noticeText.ts. -->
          <template v-for="(block, index) in blocks" :key="index">
            <h3 v-if="block.kind === 'heading'">{{ block.text }}</h3>
            <p v-else>{{ block.text }}</p>
          </template>

          <label class="consent">
            <input v-model="agreed" class="agree" type="checkbox">
            <span>{{ t('captureGuide.agree') }}</span>
          </label>

          <p v-if="missing" class="err" role="alert">{{ t('captureGuide.required') }}</p>
          <p v-if="failed" class="err" role="alert">{{ t('captureGuide.failed') }}</p>

          <button class="btn primary continue" type="button" :disabled="busy" @click="decide(true)">
            {{ busy ? t('captureGuide.continuing') : t('captureGuide.continue') }}
          </button>
          <button class="btn outline refuse" type="button" :disabled="busy" @click="decide(false)">
            {{ t('captureGuide.decline') }}
          </button>
        </template>
        <p v-else class="panel">{{ t('captureGuide.noticeLoading') }}</p>
      </section>
    </template>
  </main>
</template>

<style scoped>
main {
  max-width: 34rem;
  margin: 0 auto;
  padding: 1.5rem 1.25rem 4rem;
  display: grid;
  gap: var(--gap-section);
}

.eyebrow {
  margin: 0;
  font-size: .8rem;
  color: var(--ink-3);
}

h1 {
  margin: 0;
  font-size: 1.4rem;
  line-height: 1.25;
}

.intro {
  margin: 0;
  color: var(--ink-2);
}

.rules {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--gap);
  counter-reset: rule;
}

.rule {
  counter-increment: rule;
  display: grid;
  grid-template-columns: 1.75rem 1fr;
  gap: .25rem var(--gap);
  align-items: baseline;
}

.rule::before {
  content: counter(rule);
  grid-row: 1 / span 2;
  font-family: var(--mono);
  font-size: .95rem;
  color: var(--brand-ink);
  background: var(--brand-soft);
  border-radius: var(--radius);
  padding: .15rem 0;
  text-align: center;
}

.rule h2 {
  margin: 0;
  font-size: 1rem;
}

.rule p {
  margin: 0;
  grid-column: 2;
  color: var(--ink-2);
  font-size: .92rem;
}

.notice {
  display: grid;
  gap: var(--gap);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: var(--gap-loose);
  background: var(--surface);
}

.notice h2 {
  margin: 0;
  font-size: 1rem;
}

.notice h3 {
  margin: .35rem 0 0;
  font-size: .92rem;
  color: var(--ink);
}

.notice p {
  margin: 0;
  color: var(--ink-2);
  font-size: .92rem;
}

.consent {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: .6rem;
  align-items: start;
  margin-top: .35rem;
  border-top: 1px solid var(--line);
  padding-top: var(--gap);
  font-size: .92rem;
  cursor: pointer;
}

.consent input {
  width: 1.15rem;
  height: 1.15rem;
  margin: .1rem 0 0;
}

.panel {
  margin: 0;
  color: var(--ink-2);
}

.notice .err,
.err {
  margin: 0;
  color: var(--danger);
  font-size: .9rem;
}

/*
 * Copied rather than shared, as every other page in this app does it: there is no global button
 * stylesheet yet, and inventing one here would change screens this ticket has no business touching.
 */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
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

/* Refusing is a real choice and stays reachable, but it is not the one being offered. */
.btn.refuse {
  min-height: 2.6rem;
  border-color: var(--line-strong);
  color: var(--ink-2);
  font-size: .95rem;
  font-weight: 500;
}

.btn.refuse:hover {
  background: var(--line);
}
</style>
