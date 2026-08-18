<script setup lang="ts">
const { t } = useI18n()

useHead({ title: t('meta.titleTemplate', { page: t('pages.myQuote.title') }) })
</script>

<template>
  <main class="placeholder">
    <h1>{{ t('pages.myQuote.title') }}</h1>
    <p><em>client-only</em></p>
    <p>
      The quote the customer sees: line items, band, acceptance.

        Never renders <code>total_cost</code> or <code>margin_ratio</code> — those fields do not
        exist on the customer-facing DTOs at all.
    </p>
  </main>
</template>
