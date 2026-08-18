<script setup lang="ts">
const { t } = useI18n()

useHead({ title: t('meta.titleTemplate', { page: t('pages.capture.title') }) })
</script>

<template>
  <main class="placeholder">
    <h1>{{ t('pages.capture.title') }}</h1>
    <p><em>client-only</em></p>
    <p>
      Stage&nbsp;2: guided room-by-room capture.

        Camera is <code>&lt;input type=&quot;file&quot; accept=&quot;image/*&quot;
        capture=&quot;environment&quot;&gt;</code> — no native app needed. Photos are resized and
        re-encoded client-side, then uploaded straight to MinIO via a presigned PUT.
    </p>
  </main>
</template>
