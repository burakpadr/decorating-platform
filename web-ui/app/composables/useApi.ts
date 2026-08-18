import { createApiClient } from '@decorating/api-client'

/**
 * The typed API client, shared per Nuxt app instance.
 *
 * Calls go straight to the API — never through a Nuxt server route. The web app and the API sit on
 * the same registrable domain, so the anonymous session cookie works with
 * `Domain=.<domain>; SameSite=Lax`; `credentials: 'include'` is what carries it.
 */
export function useApi() {
  const nuxtApp = useNuxtApp()

  if (!nuxtApp._decoratingApi) {
    const { apiBase } = useRuntimeConfig().public
    nuxtApp._decoratingApi = createApiClient({ baseUrl: apiBase })
  }

  return nuxtApp._decoratingApi as ReturnType<typeof createApiClient>
}

declare module '#app' {
  interface NuxtApp {
    _decoratingApi?: ReturnType<typeof createApiClient>
  }
}
