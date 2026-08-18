# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Business operations platform for a painting and decorating company. v1 delivers the `quoting` module
plus a minimal `customer` module: a homeowner gets a painting quote without an on-site visit.

**Core principle: vision analysis produces observations, never prices.** A deterministic pricing
engine computes money from those observations plus declared measurements. Anything that blurs that
line is wrong, however convenient.

## Where authority lives

1. `docs/engineering/implementation-spec.md` — schema, contracts and algorithms. When this file and
   the spec disagree, the spec wins. Section numbers throughout the repo (§4, §5.10) refer to it.
2. `docs/product/is-akis-sureci.md` — the end-to-end business process, Turkish. **Read this before
   implementing any flow.** It defines Aşama 0–8, who decides what, the exception paths, and what the
   customer sees on each of the 13 screens. The spec tells you what a `QuoteRequest` is; this tells
   you why stage 3 comes after stage 2 and what happens when a photo is rejected a third time.
3. `docs/decisions/` — why the build diverges from the spec where it does: framework majors, the
   backend build tool, the layout, where generated and versioned assets live. Read the relevant record
   before "correcting" something back toward the spec.
4. `docs/product/v1-tasarim-dokumani.pdf` — product and design rationale. Not extractable as text in
   this environment (broken embedded font tables, no poppler installed), so it has not been read.
   Anything it contains that is not in the two documents above is currently unknown.

If you find a divergence with no decision record, that is a bug in one of them, not licence to pick a
side.

The three readable documents answer different questions and none replaces another: the workflow
document has no schema or formulas, the spec has no process or role definitions, and the decision
records have neither. Keep all three.

## Two build systems, one Makefile

Maven owns `api/`. A pnpm workspace covers `api-client/` and `web-ui/`. They are deliberately not
unified — the coupling costs more than it returns. The `Makefile` is the seam; `make help` lists
everything.

```sh
make install     # pnpm workspace
make infra       # Postgres 17 + MinIO in Docker
make dev-api     # :8080, Flyway migrates on startup
make dev-web     # :3000
make client      # regenerate openapi.json + the TypeScript client, then commit
make test        # Maven suite + Vitest + pnpm -r typecheck
make build       # jar + Nuxt output
```

`make dev-api` and `make dev-web` need `make infra` first, and Docker must be running for the Maven
suite (Testcontainers).

Per-directory guidance lives in `api/CLAUDE.md` and `web-ui/CLAUDE.md`. Read the one for the side you
are working on rather than duplicating it here.

## Layout

```
api/         Spring Boot, hexagonal per module (Maven)
api-client/  generated TypeScript client — a sibling of both apps, owned by neither
web-ui/      Nuxt PWA
docs/        engineering/ · product/ · decisions/
infra/       compose files, Caddyfile, .env.example
```

Everything still at the root is there because its tooling only finds it there — see
`docs/decisions/0004`.

## TDD is mandatory

Not a preference. Write the failing test first, watch it fail, then make it pass.

The reason is specific to this platform rather than general good practice: the product's value is a
deterministic calculation that turns observations into money. An arithmetic error in `PricingEngine`
is money lost on every quote it touches, silently, with no user-visible symptom. A test written after
the implementation codifies whatever that implementation happens to do — including the bug.

**The spec is already a test plan.** §5.10 is a worked example waiting to become a regression fixture.
§5.7 lists each modifier, so each is a case. §5.9 enumerates the band-width combinations. §17 ranks
what to cover first. Read the section before writing the test, not after.

```sh
make watch-web                              # red/green loop, ~200ms
make test-one TEST=PricingEngineTest         # one backend class
make test-one TEST=PricingEngineTest#appliesFurnishedToLabourOnly
make test                                   # everything, what CI runs
```

Two habits that go with it:

- **Prove a rule in both directions.** When you add an enforcement rule or an assertion, break it
  deliberately, confirm the build fails, then restore it. A rule nobody has watched fail is a rule
  nobody knows works. `ArchitectureRulesTest`, `SmsSegmentBudgetTest` and `districts.spec.ts` were
  each verified this way.
- **Never report a test as passing without having run it.** Run the command; paste what it said.

Frontend units run in plain node so the loop stays in the millisecond range. Only tests that need a
Nuxt runtime opt in with `// @vitest-environment nuxt` at the top of the file — making that the
default would put an app bootstrap in front of every assertion and the loop stops being tight enough
to write tests first.

The backend suite needs Docker for Testcontainers. Pure domain tests must not: `PricingEngine` has to
stay unit-testable with no Spring context and no database, which is the whole reason for its purity
rule.

## Rules that span both sides

**The contract is generated, never hand-written.** `springdoc-openapi` → `api-client/openapi.json` →
`openapi-typescript` → `api-client/src/schema.d.ts`. Both generated files are committed. After
changing any controller or DTO, run `make client` and commit the result; CI regenerates and fails on a
diff, so a backend change that breaks the frontend surfaces in the pull request that caused it.

**CI uses path filters, and `api-client/**` triggers both pipelines.** That is intentional: a contract
change must verify against both sides. Removing it from one pipeline as "redundant" has already been a
mistake once.

**Language rule.** All code, tables, columns, enums and API paths are English. Turkish appears only in
customer-facing copy: `web-ui/i18n/locales/tr.json`, `api/src/main/resources/notifications/tr/`,
`room.label`, `service_district.display_name`, and vision `notes`. §1 has the TR→EN glossary.

**Internal figures never reach a customer.** `total_cost` and `margin_ratio` exist on operator DTOs
only. Use separate response types, never conditional field stripping on a shared one.

**Modules integrate through domain events and nothing else.** Enforced, not advised — see
`docs/decisions/0005` and `ArchitectureRulesTest`.

## Status

Structural skeleton. In place: the schema and its seed, the contract pipeline, the deployment topology,
the architectural rules with tests, the event seam and outbox, the i18n layer, and versioned homes for
the vision prompt and the SMS templates. No business logic yet.

**What ships first is increment 1: the pricing engine and price book management as an internal tool,
with no customer interface at all** (workflow §12, spec §15). That is not a shortcut — the riskiest
assumption in the system is whether the engine produces figures the business would actually charge,
and testing it needs a price book and a way to enter inputs, not a website. Building stage 1 and the
capture flow on top of a wrong number means throwing that work away.

Within increment 1, order follows §17: `PricingEngine` first — pure, no infrastructure, and §5.10 is a
worked example waiting to become its regression fixture — then `RoomListDeriver`,
`ConfidenceEvaluator`, the state machine, and analysis schema validation.

**Phase 0 cannot be skipped.** The seeded price book holds market-derived placeholders, not this
business's costs. `V2__seed_price_book.sql` lists what needs real figures, including the two VAT rates
that require an accountant. §16 has the full list of external inputs — the SMS sender ID and the vision
provider's data processing agreement both have lead times, so they are worth starting early.

Phase 0's second half — the record of past jobs that validates the price list — **cannot be done: the
business has no job history to extract** (`docs/decisions/0012`). The record is built forward instead,
one row per job as it finishes, into `historical_job` and the calibration views in `V4`; intake and
`import.sql` are under `api/src/main/resources/calibration/`, the routine and column sheet at
`docs/product/tamamlanan-is-kaydi.md`, and `docs/decisions/0011` says why this is not `job_outcome`.

Two things follow, and both are easy to forget: **increment 1 ships with an unvalidated price list** —
`REAL-2026-01`'s figures are market research, and an empty `historical_job_calibration` is not
validation — and **Phase 1 no longer waits on Phase 0's second half**, so do not restore that
precondition from the spec's table without reading 0012.
