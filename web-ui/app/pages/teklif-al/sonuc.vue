<script setup lang="ts">
/**
 * Where the form lands (workflow §1.5) — the real screen is BOYA-32.
 *
 * A placeholder on purpose rather than half of the result screen. §1.5 calls this the biggest loss point
 * in the whole process: the customer who sees a range and leaves has given no phone number and cannot be
 * reached again. Three options belong here — continue, receive the range by SMS, leave — and the SMS one
 * matters far more than it looks. Half of that is worse than none, because it would look finished.
 *
 * The draft id arrives as `talep`, and the session cookie is what actually authorises the call:
 * POST /api/quote-requests/{id}/estimate answers the range (BOYA-29).
 */
const { t } = useI18n()
const route = useRoute()

useHead({ title: t('meta.titleTemplate', { page: t('pages.quoteRequest.title') }) })
</script>

<template>
  <main class="placeholder">
    <h1>{{ t('pages.quoteRequest.title') }}</h1>
    <p><em>client-only</em></p>
    <p>
      Stage&nbsp;1's result screen — BOYA-32. The range, a summary of the answers, the sentence
      explaining <em>why</em> the range is wide, and three options: continue, send it by SMS, leave.
    </p>
    <p>
      Draft: <code>{{ route.query.talep }}</code>
    </p>
    <NuxtLink to="/teklif-al">{{ t('quoteForm.back') }}</NuxtLink>
  </main>
</template>
