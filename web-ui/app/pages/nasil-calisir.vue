<script setup lang="ts">
const { t } = useI18n()

useHead({ title: t('meta.titleTemplate', { page: t('pages.howItWorks.title') }) })
</script>

<template>
  <main class="placeholder">
    <h1>{{ t('pages.howItWorks.title') }}</h1>
    <p><em>prerender</em></p>
    <p>
      Explains the three steps: form, photos, quote. Static content.
    </p>
  </main>
</template>
