<script setup lang="ts">
/**
 * Stage 2's second screen: the three shooting rules and the consent (§2.3, BOYA-39).
 *
 * A placeholder, and the room list screen links to it: §2.2 ends by sending the customer here, so the
 * route exists before the screen does rather than after.
 */
const { t } = useI18n()

useHead({ title: t('meta.titleTemplate', { page: t('pages.capture.title') }) })
</script>

<template>
  <main class="placeholder">
    <h1>{{ t('pages.capture.title') }}</h1>
    <p><em>client-only</em></p>
    <p>
      Stage&nbsp;2.3: three rules on one screen — turn the lights on, keep people out of frame, hold
      the phone still — plus what the photographs are used for, how long they are kept, and the
      consent record (<code>text_version</code>).
    </p>
    <p>
      Then §2.4's room-by-room capture. Camera is <code>&lt;input type="file" accept="image/*"
      capture="environment"&gt;</code> — no native app needed. Photos are resized and re-encoded
      client-side, then uploaded straight to MinIO via a presigned PUT.
    </p>
  </main>
</template>
