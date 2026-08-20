<script setup lang="ts">
/**
 * One price list version (§7, workflow §6).
 *
 * The three things the operator does here are the three the domain allows: correct a draft's figures,
 * raise everything by a percentage into a new version, and put a version live. A version that has
 * priced a quote cannot be edited, and the screen says so up front rather than letting a 409 explain it
 * after the typing.
 *
 * Two deliberate details. A row's save appears only when that row has changed, so nothing invites a
 * write that would do nothing. And the increase shows what it would do to a real figure before it is
 * applied, computed with the server's own rounding — a preview that disagrees by a kuruş is worse than
 * no preview.
 */
const { t } = useI18n()
const api = useApi()
const route = useRoute()
const id = route.params.id as string

const { data: version, refresh, error } = await useAsyncData(`price-book-${id}`, async () => {
  const { data, response } = await api.GET('/api/op/price-books/{id}', { params: { path: { id } } })
  if (!response.ok) {
    throw new Error(response.status === 401 || response.status === 403
      ? 'Operatör girişi gerekiyor.'
      : response.status === 404
        ? 'Bu sürüm bulunamadı.'
        : `Sürüm okunamadı (HTTP ${response.status}).`)
  }
  return data ?? null
})

useHead({
  title: t('meta.titleTemplate', { page: version.value?.versionCode ?? t('pages.priceBooks.title') }),
})

/** Inside a v-for the template loses the narrowing the outer `v-if` gave it. */
const book = computed(() => version.value!)

type ItemCode = NonNullable<typeof version.value>['items'][number]['code']
type Draft = { labourCost: string, materialCost: string, labourMinutes: string }

const failure = ref('')
const saved = ref<string>('')
const busy = ref('')

const drafts = reactive<Record<string, Draft>>({})

watch(version, book => {
  for (const item of book?.items ?? []) {
    drafts[item.code] = {
      labourCost: formatDecimal(item.labourCost),
      materialCost: formatDecimal(item.materialCost),
      labourMinutes: formatDecimal(item.labourMinutes, 1),
    }
  }
}, { immediate: true })

/** A row offers a save only when it differs from what the server holds and every field is a number. */
function isDirty(code: string): boolean {
  const item = version.value?.items.find(i => i.code === code)
  const draft = drafts[code]
  if (!item || !draft) {
    return false
  }
  const changed = draft.labourCost !== formatDecimal(item.labourCost)
    || draft.materialCost !== formatDecimal(item.materialCost)
    || draft.labourMinutes !== formatDecimal(item.labourMinutes, 1)
  return changed && isDecimal(draft.labourCost) && isDecimal(draft.materialCost)
    && isDecimal(draft.labourMinutes)
}

function hasBadField(code: string): boolean {
  const draft = drafts[code]
  return !!draft && (!isDecimal(draft.labourCost) || !isDecimal(draft.materialCost)
    || !isDecimal(draft.labourMinutes))
}

async function saveItem(code: ItemCode) {
  const draft = drafts[code]
  if (!draft || !isDirty(code)) {
    return
  }
  busy.value = code
  failure.value = ''
  saved.value = ''

  const { error: failed, response } = await api.PUT('/api/op/price-books/{id}/items/{code}', {
    params: { path: { id, code } },
    body: {
      labourCost: parseDecimal(draft.labourCost),
      materialCost: parseDecimal(draft.materialCost),
      labourMinutes: parseDecimal(draft.labourMinutes),
    },
  })
  busy.value = ''
  if (!response.ok) {
    failure.value = failed?.detail ?? `Kaydedilemedi (HTTP ${response.status}).`
    return
  }
  saved.value = code
  await refresh()
}

const increase = reactive({ target: 'LABOUR' as 'LABOUR' | 'MATERIAL' | 'ALL', percent: '15' })

/** What the increase would do to the line the operator knows best — wall paint. */
const preview = computed(() => {
  const wall = version.value?.items.find(item => item.code === 'WALL_PAINT')
  if (!wall || !isDecimal(increase.percent)) {
    return null
  }
  const percent = parseDecimal(increase.percent)
  const raisesLabour = increase.target !== 'MATERIAL'
  const raisesMaterial = increase.target !== 'LABOUR'
  return {
    from: formatDecimal(raisesLabour ? wall.labourCost : wall.materialCost),
    to: formatDecimal(raiseBy(raisesLabour ? wall.labourCost : wall.materialCost, percent)),
    half: raisesLabour && raisesMaterial
      ? t('priceBook.items.labour')
      : raisesLabour ? t('priceBook.items.labour') : t('priceBook.items.material'),
  }
})

async function applyIncrease() {
  if (!isDecimal(increase.percent)) {
    failure.value = 'Yüzde bir sayı olmalı.'
    return
  }
  busy.value = 'increase'
  failure.value = ''
  const { data, error: failed, response } = await api.POST('/api/op/price-books/{id}/bulk-increase', {
    params: { path: { id } },
    body: { target: increase.target, percent: parseDecimal(increase.percent) },
  })
  busy.value = ''
  if (!response.ok || !data) {
    failure.value = failed?.detail ?? `Zam uygulanamadı (HTTP ${response.status}).`
    return
  }
  // Straight to the produced version: it is inactive, and reviewing it is the next thing to do.
  await navigateTo(`/op/fiyat-listesi/${data.id}`)
}

async function activate() {
  busy.value = 'activate'
  failure.value = ''
  const { error: failed, response } = await api.POST('/api/op/price-books/{id}/activate', {
    params: { path: { id } },
  })
  busy.value = ''
  if (!response.ok) {
    failure.value = failed?.detail ?? `Yürürlüğe alınamadı (HTTP ${response.status}).`
    return
  }
  await refresh()
}

const RATIOS = new Set(['marginRatio', 'marginAlertThreshold', 'labourVatRate', 'materialVatRate',
  'baseBandRatio', 'grossToNetRatio', 'stage1OpeningRatio'])

const PERCENT = new Intl.NumberFormat('tr-TR', { style: 'percent', maximumFractionDigits: 2 })

function coefficient(key: string, value: number): string {
  if (RATIOS.has(key)) {
    return PERCENT.format(value)
  }
  if (key === 'crewDayCost') {
    return formatAmount(value)
  }
  return formatDecimal(value, key === 'crewSize' ? 0 : 2)
}
</script>

<template>
  <div class="screen">
    <header class="bar">
      <div class="bar-inner">
        <NuxtLink class="back" to="/op/fiyat-listesi" :aria-label="t('priceBook.actions.back')">
          <svg aria-hidden="true" viewBox="0 0 20 20" width="18" height="18">
            <path
              d="M12.5 4.5 7 10l5.5 5.5" fill="none" stroke="currentColor" stroke-width="1.8"
              stroke-linecap="round" stroke-linejoin="round"
            />
          </svg>
        </NuxtLink>
        <template v-if="version">
          <h1 class="code num">{{ book.versionCode }}</h1>
          <span
            class="pill"
            :data-tone="book.active ? 'live' : book.editable ? 'draft' : 'past'"
          >
            {{ book.active ? t('priceBook.state.active')
              : book.editable ? t('priceBook.state.draft') : t('priceBook.state.superseded') }}
          </span>
        </template>
      </div>
    </header>

    <main class="content">
      <p v-if="error" class="banner danger" role="alert">{{ error.message }}</p>

      <template v-if="version">
        <p class="meta">{{ t('priceBook.age', { age: versionAge(book.createdAt) }) }}</p>

        <p v-if="!book.editable" class="banner note">
          <svg aria-hidden="true" viewBox="0 0 20 20" width="18" height="18">
            <circle cx="10" cy="10" r="7.5" fill="none" stroke="currentColor" stroke-width="1.6" />
            <path d="M10 9v5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <circle cx="10" cy="6.2" r="0.95" fill="currentColor" />
          </svg>
          <span>{{ t('priceBook.locked') }}</span>
        </p>

        <p v-if="failure" class="banner danger" role="alert">{{ failure }}</p>

        <section class="panel">
          <h2>{{ t('priceBook.items.title') }}</h2>
          <ul class="items">
            <li v-for="item in book.items" :key="item.code" :data-dirty="isDirty(item.code)">
              <div class="item-head">
                <span class="name">{{ t(`priceBook.codes.${item.code}`) }}</span>
                <span class="unit">{{ t(`priceBook.units.${item.unit}`) }}</span>
              </div>

              <div class="fields">
                <label>
                  <span>{{ t('priceBook.items.labour') }}</span>
                  <span class="field">
                    <input
                      v-model="drafts[item.code]!.labourCost" :disabled="!book.editable"
                      class="num" inputmode="decimal" type="text"
                    >
                    <i>TL</i>
                  </span>
                </label>
                <label>
                  <span>{{ t('priceBook.items.material') }}</span>
                  <span class="field">
                    <input
                      v-model="drafts[item.code]!.materialCost" :disabled="!book.editable"
                      class="num" inputmode="decimal" type="text"
                    >
                    <i>TL</i>
                  </span>
                </label>
                <label>
                  <span>{{ t('priceBook.items.minutes') }}</span>
                  <span class="field">
                    <input
                      v-model="drafts[item.code]!.labourMinutes" :disabled="!book.editable"
                      class="num" inputmode="decimal" type="text"
                    >
                    <i>dk</i>
                  </span>
                </label>
              </div>

              <p v-if="hasBadField(item.code)" class="field-error">Sayı olmayan bir değer var.</p>

              <div v-if="book.editable && isDirty(item.code)" class="item-actions">
                <button class="btn primary" type="button" :disabled="busy === item.code"
                        @click="saveItem(item.code)">
                  {{ busy === item.code ? '…' : t('priceBook.actions.save') }}
                </button>
              </div>
              <p v-else-if="saved === item.code" class="saved" role="status">
                {{ t('priceBook.items.saved') }}
              </p>
            </li>
          </ul>
        </section>

        <section class="panel">
          <h2>{{ t('priceBook.increase.title') }}</h2>
          <p class="hint">{{ t('priceBook.increase.note') }}</p>

          <div class="segmented" role="group" :aria-label="t('priceBook.increase.target')">
            <button
              v-for="target in (['LABOUR', 'MATERIAL', 'ALL'] as const)"
              :key="target"
              type="button"
              :aria-pressed="increase.target === target"
              @click="increase.target = target"
            >
              {{ t(`priceBook.increase.targets.${target}`) }}
            </button>
          </div>

          <label class="percent">
            <span>{{ t('priceBook.increase.percent') }}</span>
            <span class="field">
              <input v-model="increase.percent" class="num" inputmode="decimal" type="text">
              <i>%</i>
            </span>
          </label>

          <p v-if="preview" class="preview">
            {{ t('priceBook.codes.WALL_PAINT') }} {{ preview.half.toLocaleLowerCase('tr') }}:
            <span class="num">{{ preview.from }}</span> →
            <span class="num strong">{{ preview.to }}</span>
          </p>

          <button class="btn primary wide" type="button" :disabled="busy === 'increase'"
                  @click="applyIncrease()">
            {{ busy === 'increase' ? '…' : t('priceBook.increase.apply') }}
          </button>
        </section>

        <section class="panel">
          <h2>{{ t('priceBook.coefficients.title') }}</h2>
          <dl class="coefficients">
            <template v-for="(value, key) in book.coefficients" :key="key">
              <dt>{{ t(`priceBook.coefficients.${key}`) }}</dt>
              <dd class="num">{{ coefficient(key, value) }}</dd>
            </template>
          </dl>
        </section>
      </template>
    </main>

    <!-- Bottom, not top: on a phone the primary action belongs where the thumb already is. -->
    <div v-if="version && !book.active" class="dock">
      <button class="btn primary wide" type="button" :disabled="busy === 'activate'"
              @click="activate()">
        {{ busy === 'activate' ? '…' : t('priceBook.actions.activate') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.screen {
  min-height: 100dvh;
  background: var(--bg);
  padding-bottom: 5rem;
}

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
  padding: 0.6rem 1rem;
  display: flex;
  align-items: center;
  gap: var(--gap);
}

.back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  margin-left: -0.5rem;
  border-radius: var(--radius-sm);
  color: var(--ink-2);
}

.back:hover {
  background: var(--surface-2);
  color: var(--ink);
}

h1 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.pill {
  margin-left: auto;
  padding: 0.15rem 0.5rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 650;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.pill[data-tone='live'] { background: var(--live); color: #fff; }
.pill[data-tone='draft'] { background: var(--brand-soft); color: var(--brand); }
.pill[data-tone='past'] { background: var(--surface-2); color: var(--ink-2); }

.content {
  max-width: 46rem;
  margin: 0 auto;
  padding: 0.85rem 1rem 1rem;
  display: grid;
  gap: var(--gap-loose);
}

.meta {
  margin: 0;
  font-size: 0.85rem;
  color: var(--ink-2);
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

.banner svg { flex: none; margin-top: 0.1rem; }
.banner.note { background: var(--surface-2); color: var(--ink-2); }
.banner.danger { background: var(--danger-soft); color: var(--danger); }

.panel {
  padding: 0.9rem;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
}

h2 {
  margin: 0 0 var(--gap-loose);
  font-size: 0.72rem;
  font-weight: 650;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: var(--ink-3);
}

.hint {
  margin: -0.35rem 0 var(--gap-loose);
  font-size: 0.85rem;
  color: var(--ink-2);
}

.items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: var(--gap);
}

.items > li {
  padding: 0.7rem;
  background: var(--surface-sunken);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
}

/* An edited row is marked, so the operator can see what they have touched before saving it. */
.items > li[data-dirty='true'] {
  background: var(--brand-soft);
  border-color: color-mix(in srgb, var(--brand) 35%, transparent);
}

.item-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--gap);
  margin-bottom: var(--gap);
}

.name {
  font-size: 0.95rem;
  font-weight: 600;
}

.unit {
  flex: none;
  padding: 0.05rem 0.4rem;
  border-radius: 999px;
  background: var(--surface-2);
  font-size: 0.7rem;
  color: var(--ink-2);
}

.fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(6.5rem, 1fr));
  gap: var(--gap);
}

label {
  display: grid;
  gap: 0.2rem;
  font-size: 0.72rem;
  font-weight: 550;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--ink-3);
}

.field {
  display: flex;
  align-items: center;
  background: var(--surface);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-sm);
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

input {
  flex: 1 1 auto;
  width: 100%;
  min-height: 2.6rem;
  padding: 0 0.5rem;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: inherit;
  /* 1rem, never smaller: iOS zooms the page in on a focused input under 16px. */
  font-size: 1rem;
  text-align: right;
}

input:focus {
  outline: none;
}

input:disabled {
  color: var(--ink-2);
}

.field-error {
  margin: var(--gap) 0 0;
  font-size: 0.8rem;
  color: var(--danger);
}

.item-actions {
  margin-top: var(--gap);
}

.saved {
  margin: var(--gap) 0 0;
  font-size: 0.8rem;
  font-weight: 550;
  color: var(--live);
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

.segmented button {
  min-height: 2.5rem;
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

.percent {
  margin-top: var(--gap-loose);
  max-width: 9rem;
}

.preview {
  margin: var(--gap-loose) 0 0;
  padding: 0.55rem 0.7rem;
  background: var(--surface-sunken);
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  color: var(--ink-2);
}

.preview .strong {
  font-weight: 650;
  color: var(--ink);
}

.coefficients {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.4rem 1rem;
  margin: 0;
  font-size: 0.9rem;
}

.coefficients dt {
  color: var(--ink-2);
}

.coefficients dd {
  margin: 0;
  font-weight: 550;
}

.btn {
  min-height: 2.75rem;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 1rem;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  font: inherit;
  font-size: 0.925rem;
  font-weight: 550;
  cursor: pointer;
  transition: background-color 0.15s ease;
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
  margin-top: var(--gap-loose);
}

.btn:disabled {
  opacity: 0.55;
  cursor: progress;
}

.dock {
  position: fixed;
  inset: auto 0 0;
  padding: 0.75rem 1rem calc(0.75rem + env(safe-area-inset-bottom));
  background: color-mix(in srgb, var(--surface) 94%, transparent);
  backdrop-filter: blur(8px);
  border-top: 1px solid var(--line);
}

.dock .btn {
  max-width: 44rem;
  margin: 0 auto;
  display: flex;
}
</style>
