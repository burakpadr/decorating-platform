# @decorating/api-client

The typed contract between `api` and `web-ui`.

Nothing in this package is written by hand except `src/index.ts`. The pipeline is:

```
springdoc-openapi  ──►  openapi.json  ──►  openapi-typescript  ──►  src/schema.d.ts
     (../api)               (here)                                       (here)
```

Both `openapi.json` and `src/schema.d.ts` are committed. CI regenerates them and fails on a diff,
so a backend DTO change that breaks the frontend is caught in the pull request that causes it
rather than at runtime.

## Regenerating

```sh
make client            # from the repo root: exports the spec, then the types
```

or, in two steps:

```sh
cd api && ./mvnw -Popenapi verify -DskipTests   # writes ../api-client/openapi.json
pnpm --filter @decorating/api-client generate   # writes src/schema.d.ts
```

The `openapi` Maven profile starts the application on port 18080 under the `openapi` *Spring*
profile, which excludes the datasource, JPA and Flyway autoconfiguration — no Postgres or MinIO
needed. It is a profile rather than part of the default lifecycle so that a plain `mvn verify` does
not start the application.

## Using it

```ts
import { createApiClient } from '@decorating/api-client'

const api = createApiClient({ baseUrl: 'https://api.example.com' })
const { data, error } = await api.GET('/api/districts')
```

Path strings and response shapes are checked against the spec, so a renamed endpoint is a compile
error rather than a 404 in production.
