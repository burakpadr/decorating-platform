<script setup lang="ts">
/**
 * Stage 2's capture screen: room by room, frame by frame (§2.4–2.7, BOYA-42).
 *
 * A placeholder, and the guidance screen links to it once consent is recorded — the route exists before
 * the screen does rather than after, as §2.2's did for §2.3. What lands here needs BOYA-41's client-side
 * pipeline first: a frame is read for its EXIF date, resized, re-encoded, scored, and only then PUT to
 * the presigned URL BOYA-40 already signs.
 */
const { t } = useI18n()

useHead({ title: t('meta.titleTemplate', { page: t('pages.capture.title') }) })
</script>

<template>
  <main class="placeholder">
    <h1>{{ t('pages.capture.title') }}</h1>
    <p><em>client-only</em></p>
    <p>
      Stage&nbsp;2.4–2.7: pick an area from the confirmed list, take the frames it asks for, preview and
      accept each one, then the close-ups of anything cracked or stained (§2.6, unlimited and skippable).
      Uploading starts the moment a frame is accepted (§2.7) — leaving all 28 to the end loses the lot on
      a dropped connection.
    </p>
    <p>
      Camera is <code>&lt;input type="file" accept="image/*" capture="environment"&gt;</code> — no native
      app needed. The quality check (§2.5) runs on the phone while the customer is still in the room, and
      its thresholds stay loose on purpose: after three rejections of the same frame, accept it and let
      the backend flag it.
    </p>
  </main>
</template>
