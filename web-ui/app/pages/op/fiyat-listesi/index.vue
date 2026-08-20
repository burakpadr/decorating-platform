<script setup lang="ts">
/**
 * The price list versions (§7, workflow §6).
 *
 * Mobile first, because the operator opens this between jobs — on a phone, in a van, on site. Every
 * control is a full-width tap target, nothing is behind a hover, and the table that would need
 * horizontal scrolling on a phone is a stack of cards instead.
 */
const { t } = useI18n()
const api = useApi()

useHead({ title: t('meta.titleTemplate', { page: t('pages.priceBooks.title') }) })

const { data: versions, refresh, status } = await useAsyncData('price-books', async () => {
  const { data } = await api.GET('/api/op/price-books')
  return data ?? []
}, { default: () => [] })

const busy = ref('')
const failure = ref('')

/** The live version first, then newest to oldest — which is the order the operator asks about them. */
const ordered = computed(() =>
  [...versions.value].sort((a, b) =>
    Number(b.active) - Number(a.active) || b.createdAt.localeCompare(a.createdAt)))

const live = computed(() => ordered.value.find(version => version.active))

async function activate(id: string) {
  busy.value = id
  failure.value = ''
  const { error } = await api.POST('/api/op/price-books/{id}/activate', { params: { path: { id } } })
  if (error) {
    failure.value = error.detail ?? 'Yürürlüğe alınamadı'
  }
  await refresh()
  busy.value = ''
}
</script>

<template>
  <main class="panel">
    <header>
      <h1>{{ t('pages.priceBooks.title') }}</h1>
      <p v-if="live && isStale(live.createdAt)" class="warn">
        {{ t('priceBook.stale', { age: versionAge(live.createdAt) }) }}
      </p>
    </header>

    <p v-if="failure" class="error" role="alert">{{ failure }}</p>
    <p v-if="status === 'pending'">…</p>
    <p v-else-if="!ordered.length">{{ t('priceBook.empty') }}</p>

    <ul v-else class="versions">
      <li v-for="version in ordered" :key="version.id" :data-active="version.active">
        <div class="row">
          <span class="code">{{ version.versionCode }}</span>
          <span class="state">
            {{ version.active ? t('priceBook.state.active') : t('priceBook.state.superseded') }}
          </span>
        </div>
        <p class="age">{{ t('priceBook.age', { age: versionAge(version.createdAt) }) }}</p>
        <div class="actions">
          <NuxtLink class="button" :to="`/op/fiyat-listesi/${version.id}`">
            {{ t('priceBook.actions.detail') }}
          </NuxtLink>
          <button
            v-if="!version.active"
            type="button"
            :disabled="busy === version.id"
            @click="activate(version.id)"
          >
            {{ t('priceBook.actions.activate') }}
          </button>
        </div>
      </li>
    </ul>
  </main>
</template>

<style scoped>
.panel {
  max-width: 44rem;
  margin: 0 auto;
  padding: 1.25rem 1rem 4rem;
}

h1 {
  font-size: 1.35rem;
  margin: 0 0 0.5rem;
}

.warn {
  background: #fff6e5;
  border-left: 3px solid #e5793c;
  padding: 0.6rem 0.75rem;
  margin: 0 0 1rem;
  font-size: 0.9rem;
}

.error {
  color: #bf2600;
  font-size: 0.9rem;
}

.versions {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.75rem;
}

.versions li {
  border: 1px solid #dfe1e6;
  border-radius: 6px;
  padding: 0.85rem;
}

.versions li[data-active='true'] {
  border-color: #006644;
  background: #f2fbf6;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 0.5rem;
}

.code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 600;
}

.state {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: #5e6c84;
}

li[data-active='true'] .state {
  color: #006644;
}

.age {
  margin: 0.35rem 0 0.75rem;
  font-size: 0.85rem;
  color: #5e6c84;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

/* 44px tall and full width on a phone: this gets tapped with a thumb, outdoors, in a hurry. */
.actions > * {
  flex: 1 1 10rem;
  min-height: 2.75rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font: inherit;
  font-size: 0.95rem;
  border: 1px solid #c1c7d0;
  border-radius: 4px;
  background: #fff;
  color: inherit;
  text-decoration: none;
  cursor: pointer;
}

.actions > *:disabled {
  opacity: 0.5;
  cursor: progress;
}
</style>
