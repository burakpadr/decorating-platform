<script setup lang="ts">
import { DISTRICTS, findDistrictBySlug } from '~/utils/districts'

const { t } = useI18n()
const route = useRoute()
const district = findDistrictBySlug(String(route.params.district))

if (!district) {
  throw createError({ statusCode: 404, statusMessage: 'District not found', fatal: true })
}

// The district name is data, not copy: it comes from the districts list and is interpolated into
// the translated string. The sentence around it lives in the i18n layer.
useHead({
  title: t('meta.district.title', { district: district.name }),
  meta: [
    { name: 'description', content: t('meta.district.description', { district: district.name }) },
  ],
})
</script>

<template>
  <main v-if="district" class="placeholder">
    <h1>{{ t('meta.district.title', { district: district.name }) }}</h1>
    <p><em>prerender</em></p>
    <p>
      One of 39 prerendered district pages. District-level local search is where this sector
      converts, so this page carries the SEO copy and a direct entry into the stage&nbsp;1 form
      with <code>{{ district.code }}</code> preselected.
    </p>

    <NuxtLink :to="`/teklif-al?district=${district.code}`">{{ t('nav.getQuote') }}</NuxtLink>

    <nav :aria-label="t('nav.otherDistricts')">
      <NuxtLink
        v-for="d in DISTRICTS"
        :key="d.code"
        :to="`/${d.slug}-boya-badana-fiyatlari`"
      >
        {{ d.name }}
      </NuxtLink>
    </nav>
  </main>
</template>
