<script setup lang="ts">
const { t } = useI18n()

useHead({ title: t('meta.titleTemplate', { page: t('pages.home.title') }) })
</script>

<template>
  <main class="placeholder">
    <h1>{{ t('pages.home.title') }}</h1>
    <p><em>prerender</em></p>
    <p>
      Marketing landing page. Entry point into the stage&nbsp;1 form.

        The homepage links to every district page — that is the internal linking that makes the
        39 prerendered pages worth having.
    </p>
  </main>
</template>
