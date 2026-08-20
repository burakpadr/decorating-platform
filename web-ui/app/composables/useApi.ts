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
    const { apiBase, operatorAuth } = useRuntimeConfig().public
    nuxtApp._decoratingApi = createApiClient({
      baseUrl: apiBase,
      // The operator realm is basic auth and the panel has no login screen yet. Empty everywhere but
      // a developer's machine, where it is what makes /op/** usable before that screen exists — see
      // the operator login work item. Never set this in production: it would ship the credentials to
      // every browser that loads the app.
      headers: operatorAuth ? { Authorization: `Basic ${btoa(operatorAuth)}` } : undefined,
    })
  }

  return nuxtApp._decoratingApi as ReturnType<typeof createApiClient>
}

declare module '#app' {
  interface NuxtApp {
    _decoratingApi?: ReturnType<typeof createApiClient>
  }
}
