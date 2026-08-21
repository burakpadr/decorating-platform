<script setup lang="ts">
/**
 * The welcome screen (workflow Aşama 0, BOYA-30).
 *
 * One screen, one button. §0.1 is explicit that nothing is asked for here — no registration, no phone,
 * no email — and the exchange table below is the reason rather than the decoration: the order of the
 * process is what makes people finish it, and the page that explains the order is the page where they
 * decide to start.
 *
 * Prerendered (see nuxt.config's routeRules), so nothing here may need the API. The district list is
 * the build-time array for exactly that reason; the form reads GET /api/districts, which is the
 * authoritative one.
 */
import { DISTRICTS } from '~/utils/districts'

const { t } = useI18n()

useHead({
  // Not the tagline: that is a promise to read, and a title tag is a search result — 68
  // characters of promise is a truncated one.
  title: t('meta.titleTemplate', { page: t('meta.home.title') }),
  meta: [{ name: 'description', content: t('pages.home.lede') }],
})

/** The three stages, in the order they happen — the sequence is the argument. */
const exchange = [
  { gives: 'step1Gives', gets: 'step1Gets' },
  { gives: 'step2Gives', gets: 'step2Gets' },
  { gives: 'step3Gives', gets: 'step3Gets' },
] as const
</script>

<template>
  <main>
    <section class="hero">
      <p class="brand">{{ t('brand.name') }}</p>
      <h1>{{ t('brand.tagline') }}</h1>
      <p class="lede">{{ t('pages.home.lede') }}</p>

      <div class="actions">
        <NuxtLink class="btn primary" to="/teklif-al">{{ t('pages.home.cta') }}</NuxtLink>
        <NuxtLink class="quiet" to="/nasil-calisir">{{ t('pages.home.howItWorks') }}</NuxtLink>
      </div>
      <p class="promise">{{ t('pages.home.noSignup') }}</p>
    </section>

    <section class="panel">
      <h2>{{ t('pages.home.exchangeTitle') }}</h2>
      <!-- A real sequence, so it is numbered: each row is only available once the one above it has
           happened, which is the whole argument the section is making. -->
      <ol class="exchange">
        <li v-for="(step, index) in exchange" :key="step.gives">
          <span class="step">{{ index + 1 }}</span>
          <span class="gives">
            <em>{{ t('pages.home.exchange.gives') }}</em>
            {{ t(`pages.home.exchange.${step.gives}`) }}
          </span>
          <span class="gets">
            <em>{{ t('pages.home.exchange.gets') }}</em>
            <strong>{{ t(`pages.home.exchange.${step.gets}`) }}</strong>
          </span>
        </li>
      </ol>
      <p class="hint">{{ t('pages.home.exchangeNote') }}</p>
    </section>

    <section class="panel">
      <h2>{{ t('pages.home.bandTitle') }}</h2>
      <p class="body">{{ t('pages.home.bandBody') }}</p>
    </section>

    <section class="panel">
      <h2>{{ t('pages.home.districtsTitle') }}</h2>
      <p class="hint">{{ t('pages.home.districtsNote') }}</p>
      <nav class="districts" :aria-label="t('pages.home.districtsTitle')">
        <NuxtLink
          v-for="district in DISTRICTS"
          :key="district.code"
          :to="`/${district.slug}-boya-badana-fiyatlari`"
        >
          {{ district.name }}
        </NuxtLink>
      </nav>
    </section>

    <footer class="closing">
      <NuxtLink class="btn primary" to="/teklif-al">{{ t('pages.home.cta') }}</NuxtLink>
      <p class="promise">{{ t('pages.home.noSignup') }}</p>
    </footer>
  </main>
</template>

<style scoped>
main {
  max-width: 46rem;
  margin: 0 auto;
  padding: 2rem 1.25rem 4rem;
  display: grid;
  gap: var(--gap-section);
  color: var(--ink);
  font-family: var(--sans);
}

.hero {
  padding: 1rem 0 0.5rem;
}

.brand {
  margin: 0 0 var(--gap-loose);
  font-size: 0.75rem;
  font-weight: 650;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--brand);
}

h1 {
  margin: 0 0 var(--gap-loose);
  /* Fluid, so the promise does not wrap into four lines on a phone — which is where most of the
     traffic arrives. */
  font-size: clamp(1.75rem, 6vw, 2.6rem);
  line-height: 1.15;
  letter-spacing: -0.02em;
  text-wrap: balance;
}

.lede,
.body {
  margin: 0;
  max-width: 34rem;
  font-size: 1.05rem;
  color: var(--ink-2);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--gap-loose) var(--gap-section);
  margin: var(--gap-section) 0 var(--gap);
}

.btn {
  display: inline-flex;
  align-items: center;
  min-height: 3rem;
  padding: 0 1.4rem;
  border-radius: var(--radius);
  font-size: 1.05rem;
  font-weight: 650;
  text-decoration: none;
}

.btn.primary {
  background: var(--brand);
  color: var(--brand-ink);
  box-shadow: var(--shadow);
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

.quiet:hover {
  color: var(--ink);
}

/* Said twice, under both buttons: it is the reason somebody presses one. */
.promise {
  margin: 0;
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
  margin: 0 0 var(--gap-loose);
  font-size: 1.15rem;
  letter-spacing: -0.01em;
}

.exchange {
  list-style: none;
  margin: 0 0 var(--gap-section);
  padding: 0;
  display: grid;
  gap: var(--gap-loose);
}

.exchange li {
  display: grid;
  grid-template-columns: auto 1fr 1fr;
  align-items: baseline;
  gap: var(--gap) var(--gap-section);
  padding: var(--gap-loose) 0;
  border-top: 1px solid var(--line);
}

.exchange li:first-child {
  border-top: 0;
  padding-top: 0;
}

.step {
  width: 1.6rem;
  height: 1.6rem;
  display: grid;
  place-items: center;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand);
  font-family: var(--mono);
  font-size: 0.8rem;
  font-weight: 650;
}

.gives,
.gets {
  display: grid;
  gap: 0.1rem;
  font-size: 0.95rem;
  color: var(--ink-2);
}

/* The label above the value, once per cell: on a phone the three columns stack and a header row
   would leave the values orphaned from their meaning. */
.gives em,
.gets em {
  font-size: 0.7rem;
  font-style: normal;
  font-weight: 650;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--ink-3);
}

.gets strong {
  color: var(--ink);
}

.hint {
  margin: 0;
  font-size: 0.9rem;
  color: var(--ink-3);
}

.districts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gap) var(--gap-loose);
  margin-top: var(--gap-section);
}

.districts a {
  padding: 0.3rem 0.6rem;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface-2);
  color: var(--ink-2);
  font-size: 0.85rem;
  text-decoration: none;
}

.districts a:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.closing {
  display: grid;
  justify-items: center;
  gap: var(--gap);
  padding: var(--gap-section) 0 0;
  text-align: center;
}

@media (max-width: 34rem) {
  .exchange li {
    grid-template-columns: auto 1fr;
  }

  .gets {
    grid-column: 2;
  }
}
</style>
