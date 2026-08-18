# 7. The i18n layer goes in before any flow is written

Date: 2026-08-18
Status: accepted

## Context

Spec §1 states the language rule plainly: customer-facing copy is Turkish and lives in the i18n layer
only, never in enum values or column names. The scaffolded frontend had no i18n layer at all, and
Turkish strings sat inline in page components — violating the rule in the app that declares it.

The v1 product is Turkish-only, so there is no immediate functional need for translation. The reason to
do it now is different: retrofitting i18n after the flows are written means touching every component,
and the flows are the next thing to be written.

## Decision

`@nuxtjs/i18n` with a single `tr` locale and `strategy: 'no_prefix'`, installed before any flow exists.
Every string the user reads comes from `web-ui/i18n/locales/tr.json`.

Codes, enums and route parameters stay English — `district=KADIKOY`. District *names* are data, not
copy: they come from the districts list and are interpolated into a translated sentence.

## Consequences

`no_prefix` keeps URLs as `/teklif-al` rather than `/tr/teklif-al`, which matters because the 39
district pages are the SEO surface and their paths are already committed to. Adding a second locale
later means changing strategy, which changes those URLs — so a second locale is a deliberate SEO
decision, not a configuration flip.

Two places cannot read the i18n layer and are duplicated on purpose, flagged in place:

- the PWA manifest in `nuxt.config.ts` — build-time metadata, keep in step with `brand.*`
- `app/utils/districts.ts` — needed at prerender time, when the API is unreachable

SMS wording is *not* in this layer. It is backend copy, under
`api/src/main/resources/notifications/tr/`, for the reasons in record 6.
