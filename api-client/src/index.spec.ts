import { describe, expect, it, vi } from 'vitest'
import { createApiClient } from './index'

/*
 * Almost everything in this package is generated. `createApiClient` is not, and one of its options is
 * load-bearing in a way that fails silently: the anonymous quote session is an httpOnly cookie bound
 * to quote_request.id, sent cross-subdomain from web-ui to api. Drop `credentials: 'include'` and the
 * cookie stops travelling — every request still succeeds, but as a different anonymous visitor, so the
 * customer loses their draft with no error anywhere.
 */
function recordingFetch() {
  return vi.fn(async (_input: Request | string | URL, _init?: RequestInit) =>
    new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }),
  )
}

async function captureRequest(fetch: ReturnType<typeof recordingFetch>): Promise<Request> {
  const [input] = fetch.mock.calls[0]!
  return input instanceof Request ? input : new Request(input)
}

describe('createApiClient', () => {
  it('sends credentials so the anonymous session cookie travels', async () => {
    const fetch = recordingFetch()
    const api = createApiClient({ baseUrl: 'https://api.example.com', fetch })

    await api.GET('/api/districts' as never)

    const request = await captureRequest(fetch)
    expect(request.credentials).toBe('include')
  })

  it('resolves paths against the configured base URL', async () => {
    const fetch = recordingFetch()
    const api = createApiClient({ baseUrl: 'https://api.example.com', fetch })

    await api.GET('/api/districts' as never)

    const request = await captureRequest(fetch)
    expect(request.url).toBe('https://api.example.com/api/districts')
  })

  it('applies the headers it was given', async () => {
    const fetch = recordingFetch()
    const api = createApiClient({
      baseUrl: 'https://api.example.com',
      headers: { 'Accept-Language': 'tr' },
      fetch,
    })

    await api.GET('/api/districts' as never)

    const request = await captureRequest(fetch)
    expect(request.headers.get('Accept-Language')).toBe('tr')
  })
})
