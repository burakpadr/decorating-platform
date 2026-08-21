<script setup lang="ts">
/**
 * The other end of a handoff link (§7's resume route, BOYA-33's SMS link and BOYA-35's QR).
 *
 * The device opening this has never had a session: that is the whole reason the token exists. The API
 * exchanges it for one and answers with the draft, and from there the customer is where they left off.
 *
 * No layout, no explanation, no button on the happy path. Somebody who tapped a link in an SMS is trying
 * to get back to a price, not to read a page about it.
 */
const { t } = useI18n()
const api = useApi()
const route = useRoute()

useHead({ title: t('meta.titleTemplate', { page: t('pages.resume.title') }) })

const failed = ref(false)

onMounted(async () => {
  const { data, response } = await api.GET('/api/quote-requests/resume/{token}',
    { params: { path: { token: String(route.params.token) } } })
  if (!response.ok || !data) {
    // An expired or invented token. Said plainly rather than redirected silently: the customer tapped a
    // link on purpose and deserves to know it is the link that is stale, not their memory.
    failed.value = true
    return
  }
  await navigateTo(`/teklif-al/sonuc?talep=${data.id}`, { replace: true })
})
</script>

<template>
  <main class="placeholder">
    <template v-if="failed">
      <p>{{ t('pages.resume.failed') }}</p>
      <NuxtLink class="btn" to="/teklif-al">{{ t('pages.resume.start') }}</NuxtLink>
    </template>
    <p v-else>{{ t('pages.resume.loading') }}</p>
  </main>
</template>

<style scoped>
main {
  max-width: 30rem;
  margin: 0 auto;
  padding: 4rem 1.25rem;
  display: grid;
  gap: var(--gap-section);
  justify-items: start;
  color: var(--ink);
  font-family: var(--sans);
}

.btn {
  min-height: 3rem;
  padding: 0 1.3rem;
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--brand);
  border-radius: var(--radius);
  background: var(--brand);
  color: var(--brand-ink);
  font-weight: 650;
  text-decoration: none;
}
</style>
