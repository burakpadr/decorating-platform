# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Backend for the decorating platform. Spring Boot 4.1, Java 21, Maven. Part of a monorepo — the
frontend lives in `../web-ui` and the generated contract in `../api-client`.

**`../docs/engineering/implementation-spec.md` is authoritative** for schema, contracts and
algorithms. When this file and the spec disagree, the spec wins. Section numbers below refer to it.

`../docs/decisions/` records where the build diverges from the spec and why. `../CLAUDE.md` covers
what spans both sides of the repo.

**Before implementing any flow, read `../docs/product/is-akis-sureci.md`.** It is the end-to-end
process — the eight stages, who decides what, and every exception path. The spec defines the pieces;
that document defines the order and the branches.

## Commands

Run from `api/`. Maven owns this directory independently of the pnpm workspace at the repo root;
the root `Makefile` is the seam between them.

```sh
make -C .. infra                        # Postgres 17 + MinIO in Docker — needed by spring-boot:run
./mvnw spring-boot:run                  # http://localhost:8080, Flyway migrates on startup
./mvnw verify                           # compile + test
./mvnw test                             # needs a Docker daemon for Testcontainers
./mvnw -Popenapi verify -DskipTests     # writes ../api-client/openapi.json
```

Single test class or method — the TDD loop:

```sh
./mvnw test -Dtest='ArchitectureRulesTest'
./mvnw test -Dtest='ArchitectureRulesTest#pricingEngineIsPure'
```

Failure reports land in `target/surefire-reports/`.

**TDD is mandatory here** — see `../CLAUDE.md` for why. Two things about this codebase make it
practical rather than aspirational:

- `PricingEngine` is a pure class with no Spring context and no database (enforced by
  `ArchitectureRulesTest`), so its tests run in milliseconds. That purity rule exists *for* the tests.
- §5 is written as a specification to implement literally, and §5.10 is a worked example. Turn the
  example into the first failing fixture, then drive each modifier in §5.7 and each band-width
  combination in §5.9 as its own case. §17 gives the order.

`VisionAnalysisPort` must be fake-able for the same reason: a suite that makes real vision calls is
unusable, so it cannot be driven test-first.

## Architecture

Modular monolith, hexagonal per module. `quoting` is v1; `customer` is minimal; `scheduling`,
`jobs`, `crew`, `procurement`, `invoicing`, `analytics` will be siblings with the same internal
shape. Every package under `com.burakpadr.decorating` carries a `package-info.java` stating what
belongs there — read it before adding a class.

```
shared/          value objects + DomainEvent; depends on no module
customer/        identity by phone; owns the customer table (has its own domain/event too)
quoting/
  domain/event     the module's ONLY public surface — see below
  domain/model     entities, value objects, state transitions
  domain/service   PricingEngine, RoomListDeriver, ConfidenceEvaluator — pure
  domain/port/in   one interface per use case
  domain/port/out  repository and gateway interfaces
  application/     use case implementations, @Transactional boundaries
  adapter/in/web   controllers, DTOs, mappers
  adapter/in/scheduler   pollers
  adapter/out/{persistence,storage,vision,notification}
config/          Spring wiring only — security realms, OpenAPI, scheduling
```

### Rules that are enforced, not suggested

`ArchitectureRulesTest` fails the build on any of these. Every rule sets `allowEmptyShould(true)`
because the module packages are still largely empty — keep that when adding rules.

1. **No framework annotations in `domain/`.** No Spring, no JPA, no Jackson, no Swagger. JPA
   entities are separate classes in `adapter/out/persistence`, mapped explicitly to domain models.
2. **`PricingEngine` depends on nothing but the JDK, `shared` and `quoting.domain`.** It takes
   `PricingInput` + `PriceBook` and returns `PricedQuote`. It must stay unit-testable with no Spring
   context and no database — the single most important testability requirement in the system.
3. **Modules see each other only through published events.** A module may import another module's
   `domain/event` package and `shared`. Nothing else. Add a module by adding one line to `MODULES`
   in `ArchitectureRulesTest` — the rule is generated per module pair from that list.
4. **Cross-module references are IDs.** `quote_request.customer_id` is a plain `uuid` with no
   foreign key and no `@ManyToOne`. This is deliberate: a database-level FK would let a future
   module quietly join across the boundary.
5. **An event may not drag its module across the boundary.** Events carry IDs and `shared` value
   objects only. Referencing `quoting.domain.model.Quote` from an event would let a subscriber reach
   the whole module and defeat the seam, so that is a fifth enforced rule.

### Integration between modules

Domain events only, via `ApplicationEventPublisher` with `@TransactionalEventListener(AFTER_COMMIT)`.
The six events of §2.4 already exist as records under `<module>/domain/event`, each implementing
`shared.DomainEvent`.

Publish them from day one even where nothing subscribes — adding the publisher later means touching
`quoting` again. `QuoteAccepted` is the one that pays off: today the only subscriber creates a
`callback_task`, and when `scheduling` arrives it subscribes to the same event without `quoting`
changing by a line.

`eventType()` returns a stable `TYPE` constant rather than the class name, because renaming a class
must not orphan rows already sitting in the outbox.

Rows land in `outbox` only where a subscriber's failure must not be silently lost — `QuoteSent` (a
lost SMS is a lost sale) and `QuoteClosed` (losing it leaves customer photographs on disk past their
retention window). Claim them the same way as `analysis_job`: `FOR UPDATE SKIP LOCKED`.

### State machine

`DRAFT → PHOTOS_PENDING → ANALYZING → PENDING_REVIEW → QUOTE_SENT → AWAITING_CONTACT → CLOSED`,
with `RECAPTURE_REQUIRED` (once only) and `SURVEY_REQUIRED` as branches, plus `CANCELLED` and
`EXPIRED` reachable from anywhere. Full diagram in §3. **Enforce transitions in the domain model —
adapters must never set `status` directly.**

## Database

Flyway owns the schema; `ddl-auto` is `validate` and must stay that way. Migrations are in
`src/main/resources/db/migration`.

- Primary keys are **UUIDv7**, generated by the application. Sequential integers would leak volume
  and allow enumeration of customer quotes.
- Enums are `varchar` + `CHECK`, never native PG enum types — value lists will change.
- Money is `numeric(14,2)` / `BigDecimal`; ratios are `numeric(6,4)`.
- Timestamps are `timestamptz` in UTC. `Europe/Istanbul` is applied at presentation only.
- No Redis. OTP codes, rate limits and sessions live in PostgreSQL.

`V2__seed_price_book.sql` contains **market-derived placeholders, not this business's costs** —
including both VAT rates, which need an accountant (§16). Replace them by creating a new
`price_book` version through the operator API, never by editing the migration: changing a
coefficient must not retroactively alter existing quotes. That versioning is why the metric
coefficients (`ceiling_height_m`, `gross_to_net_ratio`, opening areas, crew figures) live in
`price_book` rather than in config.

## Versioned text assets

Two kinds of customer- or model-facing text live in `src/main/resources`, not in code and not in the
database, so that a rollback of the application rolls the text back with it:

- `prompts/room-analysis/v1.md` + `schema.json` — the filename **is**
  `room_analysis.prompt_version`. Never edit a released version in place; add `v2.md`. Editing a
  prompt that persisted rows already reference silently rewrites the calibration history, in exactly
  the way editing a price book version would rewrite quotes.
- `notifications/tr/<TEMPLATE_CODE>.txt` — the eleven templates of §13 (seven customer, four operator). `SmsSegmentBudgetTest`
  measures each one after substituting realistic placeholder values and fails the build when it
  outgrows its segment budget. Read `notifications/README.md` before touching the wording: the
  Turkish-character rule is subtler than it looks (`ö ü Ç` are inside GSM-7, `ı ğ ş ç` are not) and
  the final copy needs legal sign-off per §16.

## Pricing

§5 is a specification to implement literally. The 13 calculation steps are ordered because
modifiers compound — reordering them changes the result. Round only at line total and grand total,
`HALF_UP`; rounding intermediate steps compounds error once modifiers stack.

The labour/material split on modifiers is not cosmetic: a furnished home consumes the same paint
and more time, so applying the furnishing surcharge to materials systematically overprices
furnished jobs.

Low confidence **widens the band and never shifts the midpoint** (§5.9). Painting surprises are
one-directional — pulling low-confidence surfaces toward an average produces systematic
underquoting.

§5.10 is a worked example; it exists to become a regression fixture.

## Vision

One call per room, all of that room's photos in the same context including `DETAIL` shots — that is
what lets the model compare walls and avoid double-counting a crack visible in two frames.

**The model produces observations, never square metres, prices or totals.** Output is validated
against the JSON schema before anything is persisted; on validation failure retry once, then fail
the job. `notes` must stay Turkish — the operator reads it.

`VisionAnalysisPort` must be fake-able. A test suite that makes real vision calls is unusable.

Room confidence is the weighted average of surface confidences, not the minimum.

## Async

No broker. `analysis_job` in PostgreSQL, claimed with `FOR UPDATE SKIP LOCKED` (query in §8), polled
by `@Scheduled`. Retry with exponential backoff `run_after = now() + 2^attempts minutes`, three
attempts, then `FAILED` and flag for the operator.

Customer-facing time promises must respect `decorating.business-hours` — a request at 23:00 must
not say "within 2 hours".

## API surface

Three realms, split into separate `SecurityFilterChain` beans in `config/SecurityConfig`: anonymous
(signed httpOnly cookie bound to `quote_request.id`), verified (short-lived token after OTP), and
operator (`/api/op/**`). The anonymous and verified filters are not implemented yet.

**Operator and customer responses are separate DTO types.** `total_cost` and `margin_ratio` must
never appear in a customer-facing DTO — do not solve this with conditional field stripping on a
shared type.

There is deliberately no bulk-approve endpoint; it would remove the only human quality gate.

## The generated contract

The `openapi` Maven profile wires `spring-boot:start` → `springdoc-openapi:generate` →
`spring-boot:stop` around the `integration-test` phase. It boots the application under the `openapi`
*Spring* profile — which excludes the datasource, JPA and Flyway autoconfiguration, so no
infrastructure is needed — fetches `/v3/api-docs` on port 18080, and writes it to
`api-client/openapi.json`. CI regenerates and fails on a diff.

Four things to preserve when touching it:

- It stays a **profile**, not part of the default lifecycle. Otherwise every `mvn verify` starts the
  application.
- `jmxPort` is `18081`. `spring-boot:start` defaults to 9001 to detect readiness, and MinIO's
  console occupies 9001 in `infra/docker-compose.dev.yml` — the default fails with a JMX timeout
  whenever local infrastructure is up.
- Pretty printing lives in `application.yml` as `springdoc.writer-with-default-pretty-printer`. The
  Maven plugin writes the response body verbatim, so without it the committed file is one line and
  every contract change is an unreadable diff.
- The server URL is pinned in `OpenApiConfig`, otherwise the committed file churns with whatever
  port it happened to be generated on.

After changing any controller or DTO, run `make -C .. client` and commit the result.

## Language

All code, tables, columns, enums and API paths are English. Turkish appears only in customer-facing
copy — the i18n layer, `room.label`, `service_district.display_name`, SMS templates, and vision
`notes`. §1 has the TR→EN glossary (`Saten alçı` → `SKIM_COAT`, `Keşif` → `SURVEY`, and the rest).

Turkish characters double SMS cost: any message containing ç, ğ, ı, ö, ş, ü drops to UCS-2, 70
characters per segment instead of 160. Keep templates short.

## Build order

The first shippable thing is an **internal tool**: the pricing engine plus price book management, no
customer-facing endpoints (workflow §12 increment 1, spec §15). Operator price-book endpoints are in
scope; `/api/quote-requests/**` is not, yet.

Within that, §17 sets the order: `PricingEngine` first (pure, no infrastructure, §5.10 as the
regression fixture), then `RoomListDeriver`, `ConfidenceEvaluator`, the state machine, and analysis
schema validation.
