import createClient, { type Client } from 'openapi-fetch'
import type { paths } from './schema'

export type { paths, components, operations } from './schema'

/** Every schema type, by its name in the OpenAPI spec. */
export type Schemas = NonNullable<
  import('./schema').components['schemas']
>

export interface ApiClientOptions {
  /** Base URL of the API, e.g. `https://api.example.com`. No trailing slash. */
  baseUrl: string
  /** Extra headers applied to every request. */
  headers?: Record<string, string>
  fetch?: typeof globalThis.fetch
}

/**
 * The anonymous flow is authenticated by a signed httpOnly cookie bound to the quote request, so
 * credentials must be sent on cross-subdomain calls (web and api share a registrable domain, which
 * is what lets the cookie work with `SameSite=Lax`).
 */
export function createApiClient(options: ApiClientOptions): Client<paths> {
  return createClient<paths>({
    baseUrl: options.baseUrl,
    credentials: 'include',
    headers: options.headers,
    fetch: options.fetch,
  })
}
