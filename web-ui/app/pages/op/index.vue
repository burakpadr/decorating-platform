<script setup lang="ts">
const { t } = useI18n()

useHead({ title: t('meta.titleTemplate', { page: t('pages.operator.title') }) })
</script>

<template>
  <main class="placeholder">
    <h1>{{ t('pages.operator.title') }}</h1>
    <p><em>client-only</em></p>
    <p>
      Operator review queue: PENDING · SURVEY · CALLBACK · SENT.

        Four actions per item — approve, adjust, convert to survey, cancel. There is deliberately
        no bulk approve: it would remove the only human quality gate in the system.
    </p>
  </main>
</template>
