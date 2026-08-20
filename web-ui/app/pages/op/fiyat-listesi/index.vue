<script setup lang="ts">
/**
 * The price list versions (§7, workflow §6).
 *
 * Designed for the way it is actually used: on a phone, at a job, in daylight, one-handed. So the live
 * version is identifiable without reading — a green edge and a pill — the age is in words rather than a
 * timestamp, and every control is a thumb-sized target. Nothing here is behind a hover.
 */
const { t } = useI18n()
const api = useApi()

useHead({ title: t('meta.titleTemplate', { page: t('pages.priceBooks.title') }) })

const { data: versions, refresh, status, error } = await useAsyncData('price-books', async () => {
  const { data, response } = await api.GET('/api/op/price-books')
  // An unauthorised request is not an empty price book. Rendering "there are no price lists" for a 401
  // tells the operator the database is empty when the truth is that nobody is logged in — and the panel
  // has no login screen yet, so this is the message that has to say so.
  if (!response.ok) {
    throw new Error(response.status === 401 || response.status === 403
      ? 'Operatör girişi gerekiyor.'
      : `Fiyat listeleri okunamadı (HTTP ${response.status}).`)
  }
  return data ?? []
}, { default: () => [] })

const busy = ref('')
const failure = ref('')

/** The live version first, then newest to oldest — the order the operator asks about them in. */
const ordered = computed(() =>
  [...versions.value].sort((a, b) =>
    Number(b.active) - Number(a.active) || b.createdAt.localeCompare(a.createdAt)))

const live = computed(() => ordered.value.find(version => version.active))

async function activate(id: string) {
  busy.value = id
  failure.value = ''
  const { error: failed, response } = await api.POST('/api/op/price-books/{id}/activate', {
    params: { path: { id } },
  })
  if (!response.ok) {
    failure.value = failed?.detail ?? `Yürürlüğe alınamadı (HTTP ${response.status}).`
  }
  await refresh()
  busy.value = ''
}
</script>

<template>
  <div class="screen">
    <header class="bar">
      <div class="bar-inner">
        <p class="eyebrow">Operatör paneli</p>
        <h1>{{ t('pages.priceBooks.title') }}</h1>
      </div>
    </header>

    <main class="content">
      <p v-if="live && isStale(live.createdAt)" class="banner warn">
        <svg aria-hidden="true" viewBox="0 0 20 20" width="18" height="18">
          <path
            d="M10 2.8 18 17H2L10 2.8Z" fill="none" stroke="currentColor" stroke-width="1.6"
            stroke-linejoin="round"
          />
          <path d="M10 8v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          <circle cx="10" cy="14.6" r="0.9" fill="currentColor" />
        </svg>
        <span>{{ t('priceBook.stale', { age: versionAge(live.createdAt) }) }}</span>
      </p>

      <p v-if="failure || error" class="banner danger" role="alert">
        {{ failure || error?.message }}
      </p>

      <ul v-if="status === 'pending'" class="versions" aria-hidden="true">
        <li v-for="n in 2" :key="n" class="card skeleton" />
      </ul>

      <p v-else-if="!error && !ordered.length" class="empty">{{ t('priceBook.empty') }}</p>

      <ul v-else-if="!error" class="versions">
        <li
          v-for="version in ordered"
          :key="version.id"
          class="card"
          :data-active="version.active"
        >
          <div class="card-head">
            <span class="code num">{{ version.versionCode }}</span>
            <span class="pill" :data-tone="version.active ? 'live' : 'past'">
              {{ version.active ? t('priceBook.state.active') : t('priceBook.state.superseded') }}
            </span>
          </div>

          <p class="age">{{ t('priceBook.age', { age: versionAge(version.createdAt) }) }}</p>

          <div class="card-actions">
            <NuxtLink class="btn ghost" :to="`/op/fiyat-listesi/${version.id}`">
              {{ t('priceBook.actions.detail') }}
              <svg aria-hidden="true" viewBox="0 0 20 20" width="16" height="16">
                <path
                  d="M7.5 4.5 13 10l-5.5 5.5" fill="none" stroke="currentColor" stroke-width="1.8"
                  stroke-linecap="round" stroke-linejoin="round"
                />
              </svg>
            </NuxtLink>
            <button
              v-if="!version.active"
              class="btn primary"
              type="button"
              :disabled="busy === version.id"
              @click="activate(version.id)"
            >
              {{ busy === version.id ? '…' : t('priceBook.actions.activate') }}
            </button>
          </div>
        </li>
      </ul>
    </main>
  </div>
</template>

<style scoped>
.screen {
  min-height: 100dvh;
  background: var(--bg);
}

/* Sticky, because the operator scrolls a list of versions and still needs to know where they are. */
.bar {
  position: sticky;
  top: 0;
  z-index: 2;
  background: color-mix(in srgb, var(--surface) 92%, transparent);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--line);
}

.bar-inner {
  max-width: 46rem;
  margin: 0 auto;
  padding: 0.75rem 1rem;
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

.content {
  max-width: 46rem;
  margin: 0 auto;
  padding: 1rem 1rem 4rem;
  display: grid;
  gap: var(--gap-loose);
}

.banner {
  display: flex;
  gap: var(--gap);
  align-items: flex-start;
  margin: 0;
  padding: 0.7rem 0.85rem;
  border-radius: var(--radius);
  font-size: 0.9rem;
}

.banner svg {
  flex: none;
  margin-top: 0.1rem;
}

.banner.warn {
  background: var(--warn-soft);
  color: var(--warn);
}

.banner.danger {
  background: var(--danger-soft);
  color: var(--danger);
}

.empty {
  margin: 0;
  padding: 2rem 1rem;
  text-align: center;
  color: var(--ink-3);
}

.versions {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--gap-loose);
}

.card {
  position: relative;
  padding: 0.9rem 0.9rem 0.85rem 1.1rem;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

/* The edge does the work a glance needs: which list are quotes priced against. */
.card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  border-radius: var(--radius) 0 0 var(--radius);
  background: var(--line-strong);
}

.card[data-active='true'] {
  background: color-mix(in srgb, var(--live-soft) 55%, var(--surface));
  border-color: color-mix(in srgb, var(--live) 35%, var(--line));
}

.card[data-active='true']::before {
  background: var(--live);
}

.card.skeleton {
  height: 7.5rem;
  background: linear-gradient(
    100deg,
    var(--surface-2) 30%,
    var(--surface-sunken) 50%,
    var(--surface-2) 70%
  );
  background-size: 300% 100%;
  animation: sweep 1.4s ease-in-out infinite;
  box-shadow: none;
}

@keyframes sweep {
  from { background-position: 100% 0; }
  to { background-position: 0 0; }
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--gap);
}

.code {
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.pill {
  flex: none;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 650;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.pill[data-tone='live'] {
  background: var(--live);
  color: #fff;
}

.pill[data-tone='past'] {
  background: var(--surface-2);
  color: var(--ink-2);
}

.age {
  margin: 0.35rem 0 0.8rem;
  font-size: 0.85rem;
  color: var(--ink-2);
}

.card-actions {
  display: flex;
  gap: var(--gap);
}

/* 44px and full width on a phone: this is tapped with a thumb, outdoors, in a hurry. */
.btn {
  flex: 1 1 auto;
  min-height: 2.75rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  padding: 0 0.9rem;
  border-radius: var(--radius-sm);
  border: 1px solid transparent;
  font: inherit;
  font-size: 0.925rem;
  font-weight: 550;
  text-decoration: none;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}

.btn.ghost {
  background: var(--surface);
  border-color: var(--line-strong);
  color: var(--ink);
}

.btn.ghost:hover {
  background: var(--surface-2);
}

.btn.primary {
  background: var(--brand);
  color: var(--brand-ink);
}

.btn.primary:hover {
  background: var(--brand-hover);
}

.btn:disabled {
  opacity: 0.55;
  cursor: progress;
}
</style>
