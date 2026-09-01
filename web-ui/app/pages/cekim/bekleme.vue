<script setup lang="ts">
/**
 * §3.2's waiting screen: when it will be ready, and that you may leave (BOYA-46).
 *
 * The screen exists because of one sentence in §3.2 — "Müşteriye teklifin ne zaman hazır olacağı
 * söylenir ve ekrandan çıkabileceği belirtilir" — and the second half matters as much as the first.
 * A customer who does not know they can close the tab sits and refreshes, and then stops trusting the
 * estimate when nothing happens.
 *
 * The time comes from the submit that got here, computed against working hours by the only side that
 * knows them. Arriving without one — a bookmark, a reload of a link somebody pasted — says nothing
 * about the hour rather than inventing it, which is the same rule §3.2 states from the other end.
 */
const { t } = useI18n()
const route = useRoute()

useHead({ title: t('meta.titleTemplate', { page: t('pages.waiting.title') }) })

const id = String(route.query.talep ?? '')
const respondBy = route.query.yanit ? String(route.query.yanit) : null

const promise = computed(() => promiseWording(respondBy, new Date()))
</script>

<template>
  <main>
    <template v-if="!id">
      <p class="panel">{{ t('waiting.incomplete') }}</p>
      <NuxtLink class="btn outline" to="/teklif-al">{{ t('waiting.goToForm') }}</NuxtLink>
    </template>

    <template v-else>
      <p class="eyebrow">{{ t('waiting.eyebrow') }}</p>
      <h1>{{ t('waiting.title') }}</h1>

      <p class="promise">{{ t(`waiting.${promise.key}`, promise.values) }}</p>
      <p class="leave">{{ t('waiting.leaveOk') }}</p>

      <section class="next">
        <h2>{{ t('waiting.whatNow') }}</h2>
        <ol>
          <li>{{ t('waiting.step1') }}</li>
          <li>{{ t('waiting.step2') }}</li>
          <li>{{ t('waiting.step3') }}</li>
        </ol>
      </section>

      <NuxtLink class="btn outline" to="/">{{ t('waiting.home') }}</NuxtLink>
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
  justify-items: start;
}

.eyebrow { margin: 0; font-size: .8rem; color: var(--ink-3); }
h1 { margin: 0; font-size: 1.4rem; line-height: 1.25; }

.promise { margin: 0; font-size: 1.1rem; }
.leave { margin: 0; color: var(--ink-2); }

.next {
  display: grid;
  gap: .5rem;
  width: 100%;
  border-top: 1px solid var(--line);
  padding-top: var(--gap-loose);
}
.next h2 { margin: 0; font-size: 1rem; }
.next ol { margin: 0; padding-left: 1.2rem; color: var(--ink-2); display: grid; gap: .3rem; }

.panel { margin: 0; color: var(--ink-2); }

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 3rem;
  padding: 0 1.4rem;
  border: 1px solid var(--brand);
  border-radius: var(--radius);
  background: var(--surface);
  color: var(--brand);
  font: inherit;
  font-size: 1.05rem;
  font-weight: 650;
  text-decoration: none;
  cursor: pointer;
}
.btn:hover { background: var(--brand-soft); }
</style>
