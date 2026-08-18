<script setup lang="ts">
const { t } = useI18n()

useHead({ title: t('meta.titleTemplate', { page: t('pages.quoteRequest.title') }) })
</script>

<template>
  <main class="placeholder">
    <h1>{{ t('pages.quoteRequest.title') }}</h1>
    <p><em>client-only</em></p>
    <p>
      Stage&nbsp;1: the eight-question form, then the instant price range.

        Every step PATCHes <code>/api/quote-requests/{id}</code>. Do not rely on localStorage —
        people abandon mid-flow and resume on another device.
    </p>
  </main>
</template>
