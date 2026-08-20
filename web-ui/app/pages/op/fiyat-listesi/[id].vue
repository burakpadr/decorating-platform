<script setup lang="ts">
/**
 * One price list version (§7, workflow §6).
 *
 * The three things the operator does here are the three the domain allows: correct a draft's figures,
 * raise everything by a percentage into a new version, and put a version live. There is no way to edit
 * a version that has priced a quote — the screen says so instead of finding out from a 409.
 */
const { t } = useI18n()
const api = useApi()
const route = useRoute()
const id = route.params.id as string

const { data: version, refresh } = await useAsyncData(`price-book-${id}`, async () => {
  const { data } = await api.GET('/api/op/price-books/{id}', { params: { path: { id } } })
  return data ?? null
})

useHead({ title: t('meta.titleTemplate', { page: version.value?.versionCode ?? t('pages.priceBooks.title') }) })

/** The item codes the contract allows — the path parameter is not a free string. */
type ItemCode = NonNullable<typeof version.value>['items'][number]['code']

const failure = ref('')
const saved = ref('')
const busy = ref(false)

const increase = reactive({ target: 'LABOUR' as 'LABOUR' | 'MATERIAL' | 'ALL', percent: 15 })

/** Item costs are edited in place, so the inputs need their own copy to hold a half-typed figure. */
const drafts = reactive<Record<string, { labourCost: string, materialCost: string, labourMinutes: string }>>({})

watch(version, book => {
  for (const item of book?.items ?? []) {
    drafts[item.code] = {
      labourCost: String(item.labourCost),
      materialCost: String(item.materialCost),
      labourMinutes: String(item.labourMinutes),
    }
  }
}, { immediate: true })

async function saveItem(code: ItemCode) {
  const draft = drafts[code]
  if (!draft) {
    return
  }
  busy.value = true
  failure.value = ''
  saved.value = ''

  const { error } = await api.PUT('/api/op/price-books/{id}/items/{code}', {
    params: { path: { id, code } },
    body: {
      labourCost: Number(draft.labourCost),
      materialCost: Number(draft.materialCost),
      labourMinutes: Number(draft.labourMinutes),
    },
  })
  if (error) {
    failure.value = error.detail ?? 'Kaydedilemedi'
  }
  else {
    saved.value = code
  }
  await refresh()
  busy.value = false
}

async function applyIncrease() {
  busy.value = true
  failure.value = ''
  const { data, error } = await api.POST('/api/op/price-books/{id}/bulk-increase', {
    params: { path: { id } },
    body: { target: increase.target, percent: increase.percent },
  })
  busy.value = false
  if (error) {
    failure.value = error.detail ?? 'Zam uygulanamadı'
    return
  }
  // Straight to the produced version: it is inactive, and reviewing it is the next thing to do.
  await navigateTo(`/op/fiyat-listesi/${data.id}`)
}

async function activate() {
  busy.value = true
  failure.value = ''
  const { error } = await api.POST('/api/op/price-books/{id}/activate', { params: { path: { id } } })
  if (error) {
    failure.value = error.detail ?? 'Yürürlüğe alınamadı'
  }
  await refresh()
  busy.value = false
}

/**
 * Inside a v-for the template loses the narrowing `v-if="version"` gave it, so the guarded block reads
 * through this instead of asserting non-null at every use.
 */
const book = computed(() => version.value!)

/** Ratios read as percentages, money as money: 0.3000 on a screen is a figure nobody checks. */
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
  return String(value)
}
</script>

<template>
  <main class="panel">
    <NuxtLink class="back" to="/op/fiyat-listesi">← {{ t('priceBook.actions.back') }}</NuxtLink>

    <template v-if="version">
      <header>
        <h1>{{ book.versionCode }}</h1>
        <p class="meta">
          <span :data-state="book.active ? 'active' : 'past'">
            {{ book.active ? t('priceBook.state.active')
              : book.editable ? t('priceBook.state.draft') : t('priceBook.state.superseded') }}
          </span>
          · {{ t('priceBook.age', { age: versionAge(book.createdAt) }) }}
        </p>
        <p v-if="!book.editable" class="note">{{ t('priceBook.locked') }}</p>
      </header>

      <p v-if="failure" class="error" role="alert">{{ failure }}</p>

      <section>
        <h2>{{ t('priceBook.items.title') }}</h2>
        <ul class="items">
          <li v-for="item in book.items" :key="item.code">
            <div class="head">
              <span class="name">{{ t(`priceBook.codes.${item.code}`) }}</span>
              <span class="unit">{{ t(`priceBook.units.${item.unit}`) }}</span>
            </div>
            <div class="fields">
              <label>
                <span>{{ t('priceBook.items.labour') }}</span>
                <input
                  v-model="drafts[item.code]!.labourCost" :disabled="!book.editable"
                  inputmode="decimal" type="text"
                >
              </label>
              <label>
                <span>{{ t('priceBook.items.material') }}</span>
                <input
                  v-model="drafts[item.code]!.materialCost" :disabled="!book.editable"
                  inputmode="decimal" type="text"
                >
              </label>
              <label>
                <span>{{ t('priceBook.items.minutes') }}</span>
                <input
                  v-model="drafts[item.code]!.labourMinutes" :disabled="!book.editable"
                  inputmode="decimal" type="text"
                >
              </label>
            </div>
            <button v-if="book.editable" type="button" :disabled="busy" @click="saveItem(item.code)">
              {{ saved === item.code ? t('priceBook.items.saved') : t('priceBook.actions.save') }}
            </button>
          </li>
        </ul>
      </section>

      <section>
        <h2>{{ t('priceBook.increase.title') }}</h2>
        <p class="note">{{ t('priceBook.increase.note') }}</p>
        <div class="fields">
          <label>
            <span>{{ t('priceBook.increase.target') }}</span>
            <select v-model="increase.target">
              <option value="LABOUR">{{ t('priceBook.increase.targets.LABOUR') }}</option>
              <option value="MATERIAL">{{ t('priceBook.increase.targets.MATERIAL') }}</option>
              <option value="ALL">{{ t('priceBook.increase.targets.ALL') }}</option>
            </select>
          </label>
          <label>
            <span>{{ t('priceBook.increase.percent') }}</span>
            <input v-model.number="increase.percent" inputmode="decimal" type="text">
          </label>
        </div>
        <button type="button" :disabled="busy" @click="applyIncrease()">
          {{ t('priceBook.increase.apply') }}
        </button>
      </section>

      <section>
        <h2>{{ t('priceBook.coefficients.title') }}</h2>
        <dl class="coefficients">
          <template v-for="(value, key) in book.coefficients" :key="key">
            <dt>{{ t(`priceBook.coefficients.${key}`) }}</dt>
            <dd>{{ coefficient(key, value) }}</dd>
          </template>
        </dl>
      </section>

      <button v-if="!version.active" class="primary" type="button" :disabled="busy" @click="activate()">
        {{ t('priceBook.actions.activate') }}
      </button>
    </template>
  </main>
</template>

<style scoped>
.panel {
  max-width: 44rem;
  margin: 0 auto;
  padding: 1rem 1rem 4rem;
}

.back {
  display: inline-block;
  padding: 0.5rem 0;
  font-size: 0.9rem;
}

h1 {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 1.25rem;
  margin: 0.5rem 0 0.25rem;
}

h2 {
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #5e6c84;
  margin: 2rem 0 0.75rem;
}

.meta {
  margin: 0 0 0.5rem;
  font-size: 0.85rem;
  color: #5e6c84;
}

.meta [data-state='active'] {
  color: #006644;
  font-weight: 600;
}

.note {
  background: #f4f5f7;
  border-left: 3px solid #c1c7d0;
  padding: 0.6rem 0.75rem;
  margin: 0 0 1rem;
  font-size: 0.875rem;
}

.error {
  color: #bf2600;
  font-size: 0.9rem;
}

.items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.75rem;
}

.items li {
  border: 1px solid #dfe1e6;
  border-radius: 6px;
  padding: 0.75rem;
}

.head {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.name {
  font-weight: 600;
}

.unit {
  font-size: 0.8rem;
  color: #5e6c84;
}

/* Three fields side by side where there is room, stacked on a phone. */
.fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr));
  gap: 0.5rem;
}

label {
  display: grid;
  gap: 0.2rem;
  font-size: 0.8rem;
  color: #5e6c84;
}

input,
select {
  /* 1rem, not smaller: iOS zooms the whole page in on a focused input under 16px. */
  font: inherit;
  font-size: 1rem;
  min-height: 2.75rem;
  padding: 0 0.5rem;
  border: 1px solid #c1c7d0;
  border-radius: 4px;
  background: #fff;
  color: inherit;
}

input:disabled {
  background: #f4f5f7;
  color: #5e6c84;
}

button {
  font: inherit;
  font-size: 0.95rem;
  min-height: 2.75rem;
  width: 100%;
  margin-top: 0.75rem;
  border: 1px solid #c1c7d0;
  border-radius: 4px;
  background: #fff;
  color: inherit;
  cursor: pointer;
}

button.primary {
  margin-top: 2rem;
  border-color: #006644;
  background: #006644;
  color: #fff;
}

button:disabled {
  opacity: 0.5;
  cursor: progress;
}

.coefficients {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 0.3rem 1rem;
  margin: 0;
  font-size: 0.9rem;
}

.coefficients dt {
  color: #5e6c84;
}

.coefficients dd {
  margin: 0;
  font-variant-numeric: tabular-nums;
}
</style>
