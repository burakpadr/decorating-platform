# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Frontend for the decorating platform. Nuxt 4 PWA, Pinia, TypeScript. Part of a pnpm workspace — the
backend lives in `../api` and the generated API client in `../api-client`.

**`../docs/engineering/implementation-spec.md` is authoritative.** §10 covers this app; section
numbers below refer to that document.

`../docs/decisions/` records where the build diverges from the spec and why. `../CLAUDE.md` covers
what spans both sides of the repo.

**Before implementing any flow, read `../docs/product/is-akis-sureci.md`.** It is the end-to-end
process — the eight stages, who decides what, and every exception path. The spec defines the pieces;
that document defines the order and the branches.

## Commands

Run from the repo root — this package is a workspace member, not a standalone project.

```sh
pnpm install                                  # whole workspace
pnpm --filter @decorating/web dev             # http://localhost:3000
pnpm --filter @decorating/web build           # also prerenders the 39 district pages
pnpm --filter @decorating/web typecheck
pnpm -r typecheck                             # web + api-client, what CI runs
make client                                   # regenerate the API contract after a backend change
```

`make dev-web` is the same as the `dev` line above. The API must be running separately
(`make infra && make dev-api`) for anything beyond static pages.

## Tests

**TDD is mandatory** — see `../CLAUDE.md` for why. Vitest, specs beside the code as `*.spec.ts`.

```sh
make watch-web                                  # red/green loop, ~200ms
pnpm --filter @decorating/web test              # once
pnpm -r test                                    # web-ui + api-client, what CI runs
```

The default environment is plain `node`, so pure units — utils, formatters, mappers — stay in the
millisecond range. A test that genuinely needs a Nuxt runtime opts in with `// @vitest-environment
nuxt` on its first line. Making that the default would put an app bootstrap in front of every
assertion and the loop stops being tight enough to write tests first.

`app/utils/districts.spec.ts` is worth reading as a pattern: it parses the SQL seed and asserts the
duplicated district list still matches it. Where a comment asks two things to stay in step, a test
should be doing the asking.

## Nuxt 4 layout

Application source lives under `app/` — `app/pages`, `app/composables`, `app/utils`,
`app/components`, `app/app.vue`. `nuxt.config.ts` and `public/` stay at the package root. Auto-imports
are on, so composables and utils need no import statement inside components.

## Route strategy

Set in `routeRules` plus `nitro.prerender.routes`. The split is deliberate (§10):

| Routes | Mode | Why |
|---|---|---|
| `/`, `/nasil-calisir`, `/{district}-boya-badana-fiyatlari` | prerender | Traffic is the real bottleneck in a self-serve funnel, and district-level local search converts best in this sector. |
| `/teklif-al/**`, `/cekim/**`, `/teklifim/**`, `/op/**` | `ssr: false` | The capture flow gains nothing from SSR, and this keeps camera and upload-queue code off the server. |

`crawlLinks` is off and the 39 district routes are enumerated explicitly, because a route rule
cannot expand a dynamic segment.

**`app/utils/districts.ts` mirrors the `service_district` table.** It exists only because the SEO
pages are prerendered at build time, when the API is unreachable. The form must still read the live
list from `GET /api/districts` — that is where `active` and `district_factor` come from. A slug in
this file with no matching row prerenders a page whose form cannot submit; keep it in step with
`V2__seed_price_book.sql`.

## Talking to the API

`useApi()` returns a typed `openapi-fetch` client built from the generated schema. Path strings and
response shapes are checked against the contract, so a renamed endpoint is a compile error rather
than a production 404.

- **Call the API directly. Do not proxy through Nuxt server routes** — it adds a hop, breaks
  presigned uploads, and hides CORS problems until production.
- `credentials: 'include'` is set on the client and matters: the anonymous session is an httpOnly
  cookie bound to `quote_request.id`. Web and API share a registrable domain so it works with
  `Domain=.<domain>; SameSite=Lax`.
- `../api-client/src/schema.d.ts` is generated. Never hand-edit it; run `make client` and commit
  the result. CI regenerates and fails on a diff. The package is a sibling of this one, not a child:
  `api` produces it and `web-ui` consumes it, so it belongs to neither.

## Behaviours that are requirements, not preferences

**Draft persistence.** `PATCH /api/quote-requests/{id}` on every step. Do not rely on localStorage —
people abandon mid-flow and resume on another device, and the desktop→mobile QR handoff depends on
server-side state.

**Camera.** `<input type="file" accept="image/*" capture="environment">`. This opens the device
camera on iOS Safari and Android Chrome; no native app is needed.

**Upload pipeline**, client-side, in this order (§9):

1. read EXIF `DateTimeOriginal`, send it as `capturedAt`
2. draw to canvas, resize to 2048 px long edge (2560 px for `DETAIL`)
3. re-encode JPEG q85 — this strips EXIF as a side effect
4. compute a quality score (Laplacian variance)
5. upload straight to MinIO via presigned PUT, in the background, with a progress indicator

**Keep quality rejection thresholds loose.** Rejecting a good photo costs far more than accepting a
mediocre one — the user gets annoyed and abandons. After three rejections of the same frame, accept
it and let the backend set `low_quality_flag`.

**Never render `total_cost` or `margin_ratio`.** Those fields do not exist on customer-facing DTOs
at all; if one appears in the generated schema for a customer endpoint, that is a backend bug.

**Recapture requests must be specific.** Not "retake your photos" but "the second wall of the living
room came out dark", linking to that exact capture screen.

## Copy

**Every string the user reads comes from `i18n/locales/tr.json`.** `@nuxtjs/i18n` is configured with
`strategy: 'no_prefix'` and a single `tr` locale — Turkish-only for v1, so URLs stay `/teklif-al`
rather than `/tr/teklif-al`. The plumbing is there anyway because retrofitting i18n after the flows
are written means touching every component.

Codes, enums and route params stay English (`district=KADIKOY`). District *names* are data, not
copy — they come from `app/utils/districts.ts` and get interpolated into a translated sentence, which
is why `meta.district.title` takes a `{district}` parameter.

Two places cannot read the i18n layer and are duplicated on purpose, both flagged in place: the PWA
manifest in `nuxt.config.ts` (build-time metadata) and `app/utils/districts.ts` (needed at prerender
time). Keep the manifest strings in step with `brand.name` / `brand.tagline`.

The TR→EN glossary is in §1 of the spec. Operator-facing screens read the vision `notes` field, which
the model is instructed to keep Turkish; SMS template wording lives in the backend under
`api/src/main/resources/notifications/tr/`, not here.

## Status

Every page under `app/pages` is a placeholder that states what belongs there. The routing, i18n
layer, PWA manifest, Pinia registration, prerender list and typed client are wired; no flow is
implemented.
