# Decorating Platform — Implementation Specification

Business operations platform for a painting and decorating company.

**Audience:** implementing agent / developer. This document is the authoritative source for schema, contracts, and algorithms. Product rationale lives in the separate design document (Turkish).

Where the build deliberately diverges from this document, `docs/decisions/` records why. This document is kept in step with those decisions; the records exist so the reasoning survives.

**The process itself is defined elsewhere.** `docs/product/is-akis-sureci.md` is the end-to-end workflow: the eight stages this document refers to by number ("stage 1 inputs", "stage 8, calibration input"), who decides what at each branch, the exception paths, and the screen-by-screen customer view. This document deliberately does not repeat it — but stages 3 through 7 appear nowhere here, so implementing a flow from this document alone is not possible.

**Language rule:** all code, tables, columns, enums, and API paths are English. All customer-facing copy is Turkish and lives in the i18n layer only — never in enum values or column names.

---

## 1. Platform Scope

The platform is a modular system covering the operational workflow of a decorating business. Modules are added over time; the platform is not a single-purpose application.

### Module map

| Module | Responsibility | Status |
|---|---|---|
| `quoting` | Estimate and quote generation, operator review, quote lifecycle | **v1 — this document** |
| `customer` | Customer identity, contact details, history across modules | **v1 — minimal** |
| `scheduling` | Job calendar, crew availability, appointment management | Planned |
| `jobs` | Job execution tracking, progress, handover records | Planned |
| `crew` | Team roster, day rates, attendance | Planned |
| `procurement` | Material purchasing, stock, consumption against jobs | Planned |
| `invoicing` | Invoices, collections, expense tracking | Planned |
| `analytics` | Profitability, estimate-vs-actual calibration reporting | Planned |

> The planned list is provisional and should be confirmed with the business before it drives any structural decision. What matters now is that `quoting` is built as *a* module rather than *the* application.

### What this means for v1

The first module to build is `quoting`, because it is the one that removes the current bottleneck: every quote today requires an on-site visit. Sections 3 onward specify that module.

Two things must be right from day one, because retrofitting them is expensive:

1. **`customer` exists as a shared concept**, not as columns on a quoting table. Every future module references the same customer.
2. **Modules integrate through domain events**, never direct package imports. See §2.4.

Everything else can be added later without restructuring.

### The quoting module in one paragraph

Homeowners get a painting quote without an on-site visit. Stage 1 is an anonymous 8-question form producing an instant price range. Stage 2 is guided room-by-room photo capture, analysed to produce an itemized quote. Every quote passes an operator review before reaching the customer.

Core principle: **vision analysis produces observations, never prices.** A deterministic pricing engine computes money from those observations plus declared measurements.

### Monorepo layout

```
decorating-platform/
├── api/                     Spring Boot, hexagonal architecture (Maven)
├── api-client/              TypeScript client generated from OpenAPI
├── web-ui/                  Nuxt, PWA
├── docs/
│   ├── engineering/         this document, deployment guide
│   └── product/             design document (Turkish, non-technical)
├── infra/                   docker-compose.yml, docker-compose.dev.yml,
│                            Caddyfile, .env.example
├── .github/workflows/
├── Makefile                 entry point across both build systems
├── package.json             pnpm workspace root
├── pnpm-workspace.yaml
├── pnpm-lock.yaml
├── .editorconfig
├── .gitignore
└── README.md
```

Five project directories: `api`, `api-client`, `web-ui`, `docs`, `infra`. Nothing else is foldered, because every remaining root file is one its tooling only finds at the repo root — pnpm resolves the workspace from `package.json` + `pnpm-workspace.yaml` + `pnpm-lock.yaml`, editors walk *up* to `.editorconfig` so nesting it would hide it from `api/src`, and both `make` and GitHub's README rendering start from the root.

Package root: `com.burakpadr.decorating`

**Build tooling.** Maven owns `api/` independently; a pnpm workspace at the repo root covers `web-ui/` and `api-client/`. Do not try to unify them under one build system — the coupling cost exceeds the benefit. A root `Makefile` provides the common entry points (`make dev`, `make build`, `make test`).

**Contract generation is the main payoff of the monorepo.** `springdoc-openapi` serves the spec from a running application, so the `openapi` Maven profile chains `spring-boot:start` → `springdoc-openapi:generate` → `spring-boot:stop` and writes the result to `api-client/openapi.json`; `openapi-typescript` then generates types into the same package. Commit both. CI regenerates and fails on diff, so a backend DTO change that breaks the frontend is caught in the same pull request rather than at runtime.

That export is a profile rather than part of the default lifecycle, so a plain `mvn verify` does not start the application. Run it with `make client`.

The generated client is a top-level sibling rather than a child of either application: `api` produces it and `web-ui` consumes it, so it belongs to neither, and the backend build has no business writing inside the frontend directory. It is still a pnpm workspace member.

**CI must use path filters.** Without them every commit rebuilds and tests both applications:

```yaml
paths: ['api/**',     'api-client/**']   # api pipeline
paths: ['web-ui/**',  'api-client/**']   # web pipeline
```

Note `api-client` triggers both — that is intentional, a contract change must verify against both sides.

**Docker builds run from the repo root** so shared packages resolve; each app has its own Dockerfile with an explicit build context. The compose files live in `infra/`, so that context is `..`.

### Stack

| Concern | Choice |
|---|---|
| Backend | Spring Boot 4.1, Java 21 |
| Backend build | Maven 3.9 via the wrapper |
| Database | PostgreSQL 17 |
| Object storage | MinIO (S3-compatible, self-hosted), `io.minio:minio` 8.5 |
| Frontend | Nuxt 4.5, Pinia 4, `@vite-pwa/nuxt` 1.1 |
| Frontend build | pnpm 11 workspace, Node 22 |
| Reverse proxy | Caddy 2 (automatic TLS) |
| Deployment | Single VPS, Docker Compose |
| Migrations | Flyway, version managed by Spring Boot |
| API contract | springdoc-openapi 3.1 → openapi-typescript 7 |
| Architecture tests | ArchUnit 1.4 (`archunit-junit5`) |

Earlier drafts of this document specified Spring Boot 3.x and Nuxt 3, which were the current
releases when it was written. Both have moved on: Spring Initializr no longer offers a 3.x line and
Boot 3.5's OSS support has ended, and Nuxt 3 is in maintenance. A greenfield project starting now
takes the current majors. Java stays at 21 — it is the LTS the toolchain pins, not a consequence of
the Boot version.

### Domain terminology (TR → EN)

| Turkish | English |
|---|---|
| Macun tamiri | `PATCH_FILLING` |
| Saten alçı | `SKIM_COAT` |
| Astar | `PRIMER` |
| İzolasyon astarı | `STAIN_BLOCK_PRIMER` |
| Kartonpiyer kesimi | `CORNICE_CUTTING` |
| Spot kesimi | `DOWNLIGHT_CUTTING` |
| Pervaz | `TRIM` |
| Petek | `RADIATOR` |
| Örtü / koruma | `MASKING` |
| Nakliye ve kurulum | `MOBILIZATION` |
| Duvar kağıdı sökümü | `WALLPAPER_STRIPPING` |
| Keşif | `SURVEY` (not "discovery") |

---

## 2. Architecture

### Package layout

```
com.company.decorating
├── shared/                       Money, PhoneNumber, DistrictCode, Address,
│                                 DomainEvent, CustomerId
├── customer/                     shared across modules
│   ├── domain/
│   ├── application/
│   └── adapter/
└── quoting/
    ├── domain/
    │   ├── event/                published events — the module's only public surface
    │   ├── model/                QuoteRequest, Room, Photo, Quote, PriceBook, RoomAnalysis
    │   ├── service/              PricingEngine, RoomListDeriver, ConfidenceEvaluator
    │   └── port/
    │       ├── in/               use case interfaces
    │       └── out/              repository + gateway interfaces
    ├── application/              use case implementations, @Transactional boundaries
    └── adapter/
        ├── in/web/               REST controllers, DTOs, mappers
        ├── in/scheduler/         job pollers
        ├── out/persistence/      JPA entities, repository implementations, mappers
        ├── out/storage/          MinIO adapter
        ├── out/vision/           vision provider adapter
        └── out/notification/     SMS adapter
```

Future modules (`scheduling`, `jobs`, `crew`, …) sit as siblings of `quoting` with the same internal structure.

### Hard rules

1. **`domain/` contains no framework annotations.** No Spring, no JPA, no Jackson. JPA entities are separate classes in `adapter/out/persistence`, mapped explicitly.
2. **`PricingEngine` has zero dependencies.** Input: `PricingInput` + `PriceBook`. Output: `PricedQuote`. Must be unit-testable without Spring context or database. This is the most important testability requirement in the system.
3. **Modules never import each other's packages.** A module may depend on `shared` and on other modules' *published events* — nothing else. `scheduling` must never reference `quoting.domain.model.Quote`.

   Concretely: each module's events live in its own `domain/event` package, and that package is the only one another module may import. It follows that an event carries IDs and `shared` value objects only — an event that referenced `quoting.domain.model.Quote` would hand a subscriber the whole module and defeat the seam. Both halves are enforced by `ArchitectureRulesTest`, because a rule that only exists in prose is a rule that erodes the first time it is inconvenient.
4. **Cross-module references use IDs, not objects.** `quote_request.customer_id` is a `uuid`, not a foreign-key-mapped entity relationship into another module. Enforce at the JPA mapping level: no `@ManyToOne` crossing a module boundary.

### 2.4 Domain events

Events are the only integration seam between modules. Publish them from day one even when nothing subscribes — adding the publisher later means touching `quoting` again.

| Event | Published by | Current subscriber | Future subscriber |
|---|---|---|---|
| `CustomerIdentified` | `customer` | — | `analytics` |
| `QuoteRequestSubmitted` | `quoting` | — | `analytics` |
| `QuoteSent` | `quoting` | `notification` | `analytics` |
| `QuoteAccepted` | `quoting` | creates `callback_task` | `scheduling`, `jobs` |
| `SurveyRequired` | `quoting` | creates `callback_task` | `scheduling` |
| `QuoteClosed` | `quoting` | schedules photo deletion | `analytics` |

`QuoteAccepted` is the important one. Today the only subscriber creates a callback task. When `scheduling` arrives it subscribes to the same event and `quoting` does not change by a single line.

Implementation: Spring's `ApplicationEventPublisher` with `@TransactionalEventListener(AFTER_COMMIT)` is sufficient at this scale. Persist events to the `outbox` table (§4.8) only when a subscriber's failure must not be silently lost — currently that applies to `QuoteSent` (SMS) and `QuoteClosed` (deletion scheduling).

`DomainEvent.eventType()` returns a stable constant rather than the class name: renaming a class must not orphan rows already sitting in the outbox.

### Inbound ports

```java
StartQuoteRequestUseCase        // create draft
UpdateQuoteRequestUseCase       // step-by-step patch
CalculateEstimateUseCase        // stage 1 range
ConfirmRoomListUseCase
RequestPhotoUploadUseCase       // returns presigned PUT
CompletePhotoUploadUseCase
SubmitQuoteRequestUseCase
AnalyzeRoomUseCase              // triggered by scheduler
GenerateQuoteUseCase
ReviewQuoteUseCase              // approve / adjust / survey / cancel
AcceptQuoteUseCase
RequestDataDeletionUseCase
```

### Outbound ports

```java
QuoteRequestRepository
PhotoRepository
PriceBookRepository
AnalysisJobRepository
QuoteRepository
PhotoStoragePort                // presigned URLs, delete
VisionAnalysisPort              // analyze one room
NotificationPort                // SMS / email
OtpPort
```

`VisionAnalysisPort` must be fake-able. Test suites that make real vision calls are unusable.

---

## 3. State Machine

```
DRAFT
  │ confirmRoomList
  ▼
PHOTOS_PENDING ◄───────────────┐
  │ submit (all required photos + OTP verified)
  ▼                            │ customer re-uploads
ANALYZING ─────────────────► RECAPTURE_REQUIRED
  │ analysis complete            (once only, then → PENDING_REVIEW)
  ▼
PENDING_REVIEW ────────────► SURVEY_REQUIRED
  │ operator approves            (low confidence or risk finding)
  │                            │ operator action
  ▼                            │
QUOTE_SENT ◄───────────────────┘
  │ customer accepts / operator converts
  ▼
AWAITING_CONTACT               reason: ACCEPTED | SURVEY | QUESTION
  │ operator marks outcome
  ▼
CLOSED                         outcome: WON | LOST | EXPIRED | CANCELLED
```

Terminal transitions from any state: `CANCELLED` (operator), `EXPIRED` (scheduler).

Enforce transitions in the domain model. Do not let adapters set status directly.

---

## 4. Database Schema

Conventions:
- Primary keys: **UUIDv7** (time-ordered; sequential integers would leak volume and allow enumeration of customer quotes)
- Money: `NUMERIC(14,2)`, `BigDecimal` in Java
- Enums: `varchar` + `CHECK` constraint. Do **not** use native PG enum types — value lists will change and migrations become painful
- Timestamps: `timestamptz`, stored UTC. `Europe/Istanbul` applied only at presentation
- Ratios/coefficients: `NUMERIC(6,4)`

### 4.1 Customer (shared module)

```sql
CREATE TABLE customer (
  id                uuid PRIMARY KEY,
  phone             varchar(20) NOT NULL UNIQUE,
  email             varchar(255),
  display_name      varchar(128),
  customer_type     varchar(16) NOT NULL DEFAULT 'INDIVIDUAL',  -- INDIVIDUAL | BUSINESS
  tax_number        varchar(20),
  default_district  varchar(32),
  created_at        timestamptz NOT NULL DEFAULT now(),
  updated_at        timestamptz NOT NULL DEFAULT now(),
  deleted_at        timestamptz
);
```

Owned by the `customer` module. Every other module references `customer.id` and nothing else.

A customer row is created **only on successful OTP verification** — stage 1 is anonymous and must not create one. Lookup is by phone: a returning customer resolves to the existing row, which is how repeat business becomes visible ("this number had a quote three months ago").

### 4.2 Quote request

```sql
CREATE TABLE quote_request (
  id                    uuid PRIMARY KEY,
  customer_id           uuid,                -- null while anonymous; set on OTP verify
  status                varchar(32)  NOT NULL,
  created_at            timestamptz  NOT NULL DEFAULT now(),
  updated_at            timestamptz  NOT NULL DEFAULT now(),

  -- stage 1 inputs
  district_code         varchar(32),
  area_input            numeric(7,2),
  area_basis            varchar(8),          -- GROSS | NET
  net_area              numeric(7,2),        -- derived
  layout                varchar(16),         -- STUDIO | ONE_PLUS_ONE | TWO_PLUS_ONE | ...
  scope                 varchar(16),         -- WHOLE_HOME | SELECTED_ROOMS
  furnishing            varchar(16),         -- EMPTY | PARTIAL | FURNISHED
  doors_included        boolean,
  door_count            integer,
  door_colour_change    boolean,
  wall_condition        varchar(16),         -- GOOD | MINOR | MAJOR | UNSURE

  -- stage 1 output
  estimate_low          numeric(14,2),
  estimate_high         numeric(14,2),

  -- pre-verification contact (moved to customer on verify, then nulled)
  pending_phone         varchar(20),
  phone_verified_at     timestamptz,

  -- handoff
  resume_token          varchar(64) UNIQUE,
  resume_token_expires  timestamptz,

  -- audit
  price_book_id         uuid REFERENCES price_book(id),
  recapture_count       integer NOT NULL DEFAULT 0,
  closed_at             timestamptz,
  close_outcome         varchar(16)
);

CREATE INDEX ON quote_request (status, created_at);
CREATE INDEX ON quote_request (customer_id) WHERE customer_id IS NOT NULL;
```

`customer_id` is a plain `uuid` with **no foreign key constraint** — it crosses a module boundary. Referential integrity is the application's responsibility, enforced in the `customer` module's API. This is deliberate: a database-level FK would let a future module quietly join across the boundary and the separation would erode within a month.

`area_input` + `area_basis` are stored raw; `net_area = area_input × 0.82` when basis is `GROSS`. Keep both — the basis feeds the range-widening rule.

### 4.3 Rooms and photos

```sql
CREATE TABLE room (
  id                uuid PRIMARY KEY,
  quote_request_id  uuid NOT NULL REFERENCES quote_request(id) ON DELETE CASCADE,
  room_type         varchar(24) NOT NULL,
  label             varchar(64) NOT NULL,     -- "Yatak odası 2" (customer-facing, TR)
  sort_order        integer NOT NULL,
  capture_complete  boolean NOT NULL DEFAULT false,
  UNIQUE (quote_request_id, sort_order)
);

CREATE TABLE photo (
  id                 uuid PRIMARY KEY,
  room_id            uuid NOT NULL REFERENCES room(id) ON DELETE CASCADE,
  role               varchar(16) NOT NULL,     -- WALL_1..WALL_4 | CEILING | DETAIL
  storage_key        varchar(512) NOT NULL,
  uploaded_at        timestamptz,
  captured_at        timestamptz,              -- read from EXIF before stripping
  width              integer,
  height             integer,
  byte_size          integer,
  quality_score      numeric(5,2),             -- client-side Laplacian variance
  low_quality_flag   boolean NOT NULL DEFAULT false,
  delete_after       timestamptz,
  deleted_at         timestamptz
);

CREATE INDEX ON photo (room_id, role);
CREATE INDEX ON photo (delete_after) WHERE deleted_at IS NULL;
```

`DETAIL` photos are unbounded per room; all other roles are unique per room.

### 4.4 Analysis

```sql
CREATE TABLE analysis_job (
  id            uuid PRIMARY KEY,
  room_id       uuid NOT NULL REFERENCES room(id) ON DELETE CASCADE,
  status        varchar(16) NOT NULL,      -- PENDING | RUNNING | DONE | FAILED
  attempts      integer NOT NULL DEFAULT 0,
  run_after     timestamptz NOT NULL DEFAULT now(),
  last_error    text,
  started_at    timestamptz,
  finished_at   timestamptz
);

CREATE INDEX ON analysis_job (status, run_after);

CREATE TABLE room_analysis (
  id                uuid PRIMARY KEY,
  room_id           uuid NOT NULL UNIQUE REFERENCES room(id) ON DELETE CASCADE,
  raw_response      jsonb NOT NULL,
  model_version     varchar(64) NOT NULL,
  prompt_version    varchar(32) NOT NULL,
  confidence        numeric(4,3) NOT NULL,
  furnishing        varchar(16),
  door_count        integer,
  window_count      integer,
  radiator_count    integer,
  cornice           boolean,
  downlight_count   integer,
  ceiling_staining  varchar(16),
  ceiling_filler    varchar(16),
  created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE surface_finding (
  id                  uuid PRIMARY KEY,
  room_analysis_id    uuid NOT NULL REFERENCES room_analysis(id) ON DELETE CASCADE,
  surface_id          varchar(16) NOT NULL,   -- WALL_1..WALL_4 | ROOM_GENERAL
  coating             varchar(16) NOT NULL,   -- PAINTED | TILE | WOOD | BRICK
  current_tone        varchar(16) NOT NULL,   -- LIGHT | MEDIUM | DARK
  filler_ratio        varchar(16) NOT NULL,   -- NONE | LOW | MEDIUM | HIGH | FULL
  skim_coat_required  boolean NOT NULL,
  crack_level         varchar(16) NOT NULL,   -- NONE | HAIRLINE | VISIBLE | STRUCTURAL
  moisture            varchar(16) NOT NULL,   -- NONE | STAIN | ACTIVE
  wallpaper           boolean NOT NULL,
  confidence          numeric(4,3) NOT NULL
);
```

**Why both raw and normalised:** `raw_response` is the audit trail and lets you compare prompt versions. `surface_finding` is what the pricing engine and calibration queries read — the engine must not parse JSON, and calibration reports must be plain SQL joins against `quote_adjustment`.

**`prompt_version` is mandatory.** Without it, results from different prompts become incomparable and calibration data is worthless.

Prompts live in the deployed artifact, at `api/src/main/resources/prompts/room-analysis/<version>.md`, with the response schema beside them as `schema.json`. The filename *is* `prompt_version`. Never edit a released version in place — a prompt is an input to persisted analysis results in exactly the way a price book version is an input to persisted quotes, so editing one rewrites history silently. Add the next version instead.

Rooms captured with corner shots (kitchen, bathroom, hallway) produce a single `ROOM_GENERAL` surface. Rooms with 4 wall photos produce `WALL_1`..`WALL_4`.

### 4.5 Price book (all versioned)

```sql
CREATE TABLE price_book (
  id                      uuid PRIMARY KEY,
  version_code            varchar(32) NOT NULL UNIQUE,
  active                  boolean NOT NULL DEFAULT false,
  created_at              timestamptz NOT NULL DEFAULT now(),

  ceiling_height_m        numeric(4,2) NOT NULL DEFAULT 2.70,
  gross_to_net_ratio      numeric(5,4) NOT NULL DEFAULT 0.82,
  stage1_opening_ratio    numeric(5,4) NOT NULL DEFAULT 0.12,
  door_opening_m2         numeric(5,2) NOT NULL DEFAULT 1.90,
  window_opening_m2       numeric(5,2) NOT NULL DEFAULT 2.20,

  crew_size               integer NOT NULL DEFAULT 3,
  crew_hours_per_day      numeric(4,2) NOT NULL DEFAULT 8.00,
  crew_day_cost           numeric(14,2) NOT NULL,
  day_rounding_tolerance  numeric(4,2) NOT NULL DEFAULT 0.25,

  margin_ratio            numeric(5,4) NOT NULL DEFAULT 0.30,
  margin_alert_threshold  numeric(5,4) NOT NULL DEFAULT 0.20,
  survey_amount_factor    numeric(5,2) NOT NULL DEFAULT 2.00,  -- × average job value

  labour_vat_rate         numeric(5,4) NOT NULL,
  material_vat_rate       numeric(5,4) NOT NULL,

  base_band_ratio         numeric(5,4) NOT NULL DEFAULT 0.12
);

CREATE UNIQUE INDEX ON price_book (active) WHERE active = true;

CREATE TABLE price_book_item (
  id              uuid PRIMARY KEY,
  price_book_id   uuid NOT NULL REFERENCES price_book(id) ON DELETE CASCADE,
  code            varchar(32) NOT NULL,
  unit            varchar(16) NOT NULL,      -- SQM | UNIT | ROOM | LUMP_SUM
  labour_cost     numeric(14,2) NOT NULL,
  material_cost   numeric(14,2) NOT NULL,
  labour_minutes  numeric(8,2) NOT NULL,
  UNIQUE (price_book_id, code)
);

CREATE TABLE price_modifier (
  id              uuid PRIMARY KEY,
  price_book_id   uuid NOT NULL REFERENCES price_book(id) ON DELETE CASCADE,
  code            varchar(32) NOT NULL,
  factor          numeric(6,4) NOT NULL,
  applies_to      varchar(16) NOT NULL,      -- LABOUR | MATERIAL | BOTH
  scope_items     jsonb,                     -- null = all items
  UNIQUE (price_book_id, code)
);

CREATE TABLE room_type_config (
  id                uuid PRIMARY KEY,
  price_book_id     uuid NOT NULL REFERENCES price_book(id) ON DELETE CASCADE,
  room_type         varchar(24) NOT NULL,
  area_weight       numeric(5,2) NOT NULL,
  perimeter_factor  numeric(5,2) NOT NULL,
  paintable_ratio   numeric(5,4) NOT NULL,
  required_photos   jsonb NOT NULL,          -- ["WALL_1","WALL_2","WALL_3","WALL_4","CEILING"]
  UNIQUE (price_book_id, room_type)
);

CREATE TABLE service_district (
  id                uuid PRIMARY KEY,
  price_book_id     uuid NOT NULL REFERENCES price_book(id) ON DELETE CASCADE,
  district_code     varchar(32) NOT NULL,
  display_name      varchar(64) NOT NULL,
  active            boolean NOT NULL DEFAULT true,
  district_factor   numeric(6,4) NOT NULL DEFAULT 1.0000,
  UNIQUE (price_book_id, district_code)
);
```

Metric coefficients live in the price book on purpose: changing a coefficient must not retroactively alter existing quotes.

### 4.6 Quote

```sql
CREATE TABLE quote (
  id                uuid PRIMARY KEY,
  quote_request_id  uuid NOT NULL REFERENCES quote_request(id) ON DELETE CASCADE,
  price_book_id     uuid NOT NULL REFERENCES price_book(id),
  revision          integer NOT NULL DEFAULT 1,
  status            varchar(24) NOT NULL,     -- DRAFT | SENT | ACCEPTED | EXPIRED | SUPERSEDED

  total_cost        numeric(14,2) NOT NULL,   -- internal only
  subtotal          numeric(14,2) NOT NULL,   -- ex-VAT sale
  vat_amount        numeric(14,2) NOT NULL,
  total             numeric(14,2) NOT NULL,
  band_low          numeric(14,2) NOT NULL,
  band_high         numeric(14,2) NOT NULL,
  margin_ratio      numeric(5,4) NOT NULL,
  minimum_applied   boolean NOT NULL DEFAULT false,

  estimated_days    integer NOT NULL,
  total_wall_sqm    numeric(8,2) NOT NULL,
  total_ceiling_sqm numeric(8,2) NOT NULL,

  valid_until       timestamptz,
  sent_at           timestamptz,
  created_by        varchar(16) NOT NULL,     -- SYSTEM | OPERATOR
  created_at        timestamptz NOT NULL DEFAULT now(),
  UNIQUE (quote_request_id, revision)
);

CREATE TABLE quote_line_item (
  id                uuid PRIMARY KEY,
  quote_id          uuid NOT NULL REFERENCES quote(id) ON DELETE CASCADE,
  item_code         varchar(32) NOT NULL,
  quantity          numeric(10,2) NOT NULL,
  unit              varchar(16) NOT NULL,
  labour_cost       numeric(14,2) NOT NULL,
  material_cost     numeric(14,2) NOT NULL,
  applied_modifiers jsonb NOT NULL,           -- [{"code":"FURNISHED","factor":1.25}]
  line_total        numeric(14,2) NOT NULL,
  labour_minutes    numeric(10,2) NOT NULL,
  sort_order        integer NOT NULL
);

CREATE TABLE quote_adjustment (
  id            uuid PRIMARY KEY,
  quote_id      uuid NOT NULL REFERENCES quote(id) ON DELETE CASCADE,
  item_code     varchar(32) NOT NULL,
  finding_path  varchar(128),                 -- "LIVING_ROOM.WALL_3.skim_coat_required"
  old_quantity  numeric(10,2),
  new_quantity  numeric(10,2),
  operator_id   uuid,
  created_at    timestamptz NOT NULL DEFAULT now()
);
```

`applied_modifiers` answers "why does this line have a 1.5 factor" six months later.

`quote_adjustment` is the calibration dataset. Structured, not free text.

### 4.7 Operations

```sql
CREATE TABLE callback_task (
  id                uuid PRIMARY KEY,
  quote_request_id  uuid NOT NULL REFERENCES quote_request(id) ON DELETE CASCADE,
  reason            varchar(16) NOT NULL,   -- ACCEPTED | SURVEY | QUESTION
  preferred_slot    varchar(16),            -- MORNING | AFTERNOON | EVENING
  status            varchar(24) NOT NULL,   -- OPEN | WON | THINKING | UNREACHABLE | LOST
  overdue_flagged   boolean NOT NULL DEFAULT false,
  notes             text,
  created_at        timestamptz NOT NULL DEFAULT now(),
  resolved_at       timestamptz
);

CREATE INDEX ON callback_task (status, created_at);

CREATE TABLE job_outcome (               -- stage 8, calibration input
  id                uuid PRIMARY KEY,
  quote_request_id  uuid NOT NULL UNIQUE REFERENCES quote_request(id),
  actual_total      numeric(14,2) NOT NULL,
  actual_days       integer NOT NULL,
  actual_cost       numeric(14,2),
  notes             text,
  recorded_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE consent (
  id                uuid PRIMARY KEY,
  quote_request_id  uuid NOT NULL REFERENCES quote_request(id) ON DELETE CASCADE,
  consent_type      varchar(32) NOT NULL,   -- PROCESSING | RETENTION_FOR_IMPROVEMENT
  granted           boolean NOT NULL,
  text_version      varchar(32) NOT NULL,
  ip_address        inet,
  created_at        timestamptz NOT NULL DEFAULT now(),
  revoked_at        timestamptz
);

CREATE TABLE notification (
  id                uuid PRIMARY KEY,
  quote_request_id  uuid REFERENCES quote_request(id) ON DELETE CASCADE,
  channel           varchar(16) NOT NULL,   -- SMS | EMAIL
  template_code     varchar(48) NOT NULL,
  recipient         varchar(255) NOT NULL,
  status            varchar(16) NOT NULL,   -- QUEUED | SENT | FAILED
  provider_ref      varchar(128),
  sent_at           timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE rate_limit_counter (
  id            uuid PRIMARY KEY,
  scope_key     varchar(128) NOT NULL,      -- "phone:+9053..." | "ip:1.2.3.4"
  bucket        varchar(32) NOT NULL,       -- ANALYSIS | OTP
  window_start  timestamptz NOT NULL,
  count         integer NOT NULL DEFAULT 0,
  UNIQUE (scope_key, bucket, window_start)
);

CREATE TABLE deletion_request (
  id                uuid PRIMARY KEY,
  quote_request_id  uuid NOT NULL REFERENCES quote_request(id),
  requested_at      timestamptz NOT NULL DEFAULT now(),
  approved_at       timestamptz,
  executed_at       timestamptz,
  operator_id       uuid
);
```

```sql
CREATE TABLE service_area_waitlist (
  id                    uuid PRIMARY KEY,
  district_code         varchar(32) NOT NULL,
  phone                 varchar(20) NOT NULL,
  quote_request_id      uuid REFERENCES quote_request(id) ON DELETE SET NULL,
  consent_text_version  varchar(32) NOT NULL,
  created_at            timestamptz NOT NULL DEFAULT now(),
  notified_at           timestamptz,
  UNIQUE (district_code, phone)
);

CREATE INDEX ON service_area_waitlist (district_code) WHERE notified_at IS NULL;
```

A visitor whose district is not served is eliminated by the first question of stage 1 and may instead ask to be told when it opens (`is-akis-sureci.md` §7 decision 1, §8). This is the only place holding a phone number for someone who never became a customer, which is why it is separate from `customer` — a customer row is created only on OTP verification and these visitors never verify.

`district_code` is deliberately **not** a foreign key into `service_district`: the whole point is that the district is not in that table. The unique constraint carries the requirement — a second signup must not create a second row, or opening the district later texts the same person twice.

`rate_limit_counter.bucket` therefore accepts `WAITLIST` as well as `ANALYSIS` and `OTP`. Collecting a phone number is an abuse vector even though no SMS is sent at signup time.

No Redis. OTP codes, rate limits, and sessions live in PostgreSQL. Adding a second datastore to a single VPS is not worth it at this scale.

### 4.8 Outbox

```sql
CREATE TABLE outbox (
  id            uuid PRIMARY KEY,
  event_type    varchar(64) NOT NULL,
  aggregate_id  uuid NOT NULL,
  payload       jsonb NOT NULL,
  occurred_at   timestamptz NOT NULL,
  created_at    timestamptz NOT NULL DEFAULT now(),
  published_at  timestamptz,
  attempts      integer NOT NULL DEFAULT 0,
  run_after     timestamptz NOT NULL DEFAULT now(),
  last_error    text
);

CREATE INDEX ON outbox (run_after) WHERE published_at IS NULL;
CREATE INDEX ON outbox (aggregate_id);
```

Only the events whose loss would be invisible go through here — `QuoteSent` and `QuoteClosed` (§2.4). Everything else is published in-process and nowhere else.

Claimed exactly like `analysis_job`: `FOR UPDATE SKIP LOCKED` with exponential backoff on `run_after`. There is no second mechanism to learn.

---

## 5. Pricing Engine

Pure domain service. No I/O. This section is the specification — implement it literally.

### 5.1 Input contract

```java
record PricingInput(
    String districtCode,
    BigDecimal netArea,
    boolean areaWasGross,
    List<RoomInput> rooms,
    Furnishing furnishing,
    int doorCount,
    boolean doorColourChange,
    boolean hasElevator,
    boolean rush,
    Source source                       // STAGE_1 | STAGE_2
) {}

record RoomInput(
    RoomType type,
    // STAGE_2 only; empty for STAGE_1
    List<SurfaceInput> surfaces,
    CeilingInput ceiling,
    Integer doorCount,
    Integer windowCount,
    Integer radiatorCount,
    // STAGE_1 only
    WallCondition declaredCondition
) {}
```

Stage 1 and stage 2 build the same object. The engine must not know which produced it.

### 5.2 Calculation order

Fixed. Modifiers compound, so order changes the result.

```
1.  Room area allocation
2.  Wall gross area
3.  Coating deduction
4.  Opening deduction
5.  Line item quantities
6.  Item-level modifiers (DARK_TO_LIGHT)
7.  Labour modifiers (FURNISHED, RUSH)
8.  Subtotal
9.  Mobilization + floor access
10. District factor
11. Minimum check
12. Margin
13. VAT
```

### 5.3 Step 1 — Room area allocation

```
roomArea(i) = netArea × areaWeight(i) / Σ areaWeight
```

Defaults for `room_type_config`:

| room_type | area_weight | perimeter_factor | paintable_ratio | required_photos |
|---|---|---|---|---|
| `LIVING_ROOM` | 3.0 | 4.1 | 1.00 | 4 walls + ceiling |
| `MASTER_BEDROOM` | 1.5 | 4.1 | 1.00 | 4 walls + ceiling |
| `BEDROOM` | 1.2 | 4.1 | 1.00 | 4 walls + ceiling |
| `STUDY` | 1.0 | 4.1 | 1.00 | 4 walls + ceiling |
| `KITCHEN` | 1.1 | 4.3 | 0.65 | 2 corners + ceiling |
| `BATHROOM` | 0.5 | 4.2 | 0.20 | 1 general + ceiling |
| `HALLWAY` | 0.8 | 5.5 | 1.00 | 2 corners + ceiling |
| `BALCONY` | 0.4 | 4.3 | 1.00 | 1 general + ceiling |

### 5.4 Step 2 — Wall area

```
wallGross(i) = perimeterFactor(i) × sqrt(roomArea(i)) × ceilingHeight
```

`perimeter_factor` varies by type because the square-room assumption (4.0) badly underestimates elongated spaces. A 1.2 × 6.5 m hallway has a real perimeter ~40% above the square assumption. The 4.1 used for rooms derives from a typical 1.4:1 rectangle, not a square.

### 5.5 Steps 3–4 — Deductions

```
afterCoating(i) = wallGross(i) × paintableRatio(i)

STAGE_1:  wallNet(i) = afterCoating(i) × (1 − stage1OpeningRatio)
STAGE_2:  wallNet(i) = afterCoating(i)
                     − doorCount(i)   × doorOpeningM2
                     − windowCount(i) × windowOpeningM2
          (floor at 0.60 × afterCoating(i) as a sanity guard)

ceilingArea(i) = roomArea(i)          // no deduction
```

In STAGE_2, `paintableRatio` comes from `surface_finding.coating` rather than the room-type default: a surface with `coating != PAINTED` is excluded entirely.

### 5.6 Step 5 — Line item quantities

| Item code | Quantity source |
|---|---|
| `WALL_PAINT` | Σ wallNet |
| `CEILING_PAINT` | Σ ceilingArea |
| `PATCH_FILLING` | Σ wallNet(i) × fillerRatio(i) |
| `SKIM_COAT` | Σ wallNet(i) where `skim_coat_required` |
| `PRIMER` | Σ wallNet(i) where skim coat applied or tone is `DARK` |
| `STAIN_BLOCK_PRIMER` | Σ wallNet(i) where `moisture != NONE` |
| `WALLPAPER_STRIPPING` | Σ wallNet(i) where `wallpaper` |
| `DOOR_PAINT` | declared `door_count` |
| `TRIM_PAINT` | Σ window counts (stage 2 only, optional upsell) |
| `RADIATOR_PAINT` | Σ radiator counts (stage 2 only, optional upsell) |
| `DOWNLIGHT_CUTTING` | Σ downlight counts |
| `CORNICE_CUTTING` | count of rooms with `cornice = true` |
| `MASKING` | room count |
| `MOBILIZATION` | 1 |

Filler ratio band → numeric:

| Band | Ratio |
|---|---|
| `NONE` | 0.00 |
| `LOW` | 0.15 |
| `MEDIUM` | 0.35 |
| `HIGH` | 0.60 |
| `FULL` | 1.00 |

Stage 1 `wall_condition` → synthetic findings applied to every surface:

| Declared | filler ratio | skim coat | band widening |
|---|---|---|---|
| `GOOD` | 0.00 | none | — |
| `MINOR` | 0.15 | none | — |
| `MAJOR` | 0.40 | 25% of walls | — |
| `UNSURE` | 0.20 | none | +0.15 |

### 5.7 Steps 6–7 — Modifiers

| Code | Factor | Applies to | Condition |
|---|---|---|---|
| `FURNISHED` | 1.25 | `LABOUR` | `furnishing = FURNISHED` (0.5× the delta for `PARTIAL`) |
| `DARK_TO_LIGHT` | 1.50 | `BOTH` | tone is `DARK` — scope: `WALL_PAINT`, `DOOR_PAINT` |
| `NO_ELEVATOR` | 1.20 | `BOTH` | scope: `MOBILIZATION` |
| `RUSH` | 1.25 | `LABOUR` | customer requested |
| `DISTRICT` | per district | `BOTH` | applied at step 10 to subtotal |

**The labour/material split exists for this reason.** A furnished home consumes the same paint and more time — applying the furnishing surcharge to materials systematically overprices furnished jobs. Conversely `DARK_TO_LIGHT` means a third coat: more of both.

### 5.8 Steps 11–13 — Minimum, margin, VAT

```
totalMinutes   = Σ (quantity × labourMinutes × labourModifiers)
personHours    = totalMinutes / 60
days           = personHours / (crewSize × crewHoursPerDay)
billableDays   = ceil(days − dayRoundingTolerance)

minimumCost    = billableDays × crewDayCost
totalCost      = max(Σ lineCosts, minimumCost)

subtotal       = totalCost × (1 + marginRatio)
vatAmount      = (labourPortion × labourVatRate) + (materialPortion × materialVatRate)
total          = subtotal + vatAmount
```

The 0.25 tolerance: rounding 1.1 days up to 2 is unfair to the customer, but 1.4 days must round to 2 — a crew will not take another job for half a day.

Round only at line total and grand total, `HALF_UP`. Rounding intermediate steps compounds error once modifiers stack.

### 5.9 Band width

```
bandRatio = baseBandRatio
          + (wallCondition == UNSURE      ? 0.15 : 0)
          + (areaWasGross                 ? 0.05 : 0)
          + (doorCount estimated          ? 0.03 : 0)
          + (STAGE_2: (1 − avgSurfaceConfidence) × 0.40)

bandLow  = total × (1 − bandRatio)
bandHigh = total × (1 + bandRatio)
```

**Low confidence widens the band; it never shifts the midpoint.** Painting surprises are one-directional — nobody opens a wall and finds it better than expected. Pulling low-confidence surfaces toward an average produces systematic underquoting.

### 5.10 Worked example (regression test fixture)

Input: `THREE_PLUS_ONE`, 92 m² net, Kadıköy, furnished, 8 doors, colour change, `MINOR` wall condition.

Room allocation (Σ weights = 9.3):

| Room | Area | Wall gross | After coating |
|---|---|---|---|
| Living room | 29.7 | 60.3 | 60.3 |
| Master bedroom | 14.8 | 42.6 | 42.6 |
| Bedroom × 2 | 23.7 | 76.4 | 76.4 |
| Kitchen | 10.9 | 38.3 | 24.9 |
| Bathroom | 4.9 | 25.1 | 5.0 |
| Hallway | 7.9 | 41.7 | 41.7 |
| **Total** | **92.0** | **284.4** | **250.9** |

After 12% opening deduction: **wall 221 m², ceiling 92 m²**.

```
WALL_PAINT        221 × 100    =  22,100
CEILING_PAINT      92 × 108    =   9,936
PATCH_FILLING      33 ×  65    =   2,145
DOOR_PAINT     8 × 500 × 1.50  =   6,000
MASKING             7 × 177    =   1,239
MOBILIZATION                   =   1,900
                                  -------
Subtotal cost                     43,320
FURNISHED (labour × 0.25)          6,699
DISTRICT (1.05)                    2,501
                                  -------
Total cost                        52,520
Margin 30%                        15,756
                                  -------
Subtotal (ex-VAT)                 68,276

Minutes 3,293 → 54.9 person-hours → 68.6 with furnishing
3 crew × 8h → billableDays = 3
Minimum = 3 × 4,500 = 13,500 → not binding
```

### 5.11 Default price book seed

Costs, not sale prices. Margin is applied at step 12. These land in the 110–180 TL/m² market band at 30% margin.

| code | unit | labour_cost | material_cost | labour_minutes |
|---|---|---|---|---|
| `WALL_PAINT` | SQM | 62 | 38 | 6 |
| `CEILING_PAINT` | SQM | 70 | 38 | 8 |
| `PATCH_FILLING` | SQM | 50 | 15 | 12 |
| `SKIM_COAT` | SQM | 100 | 42 | 22 |
| `PRIMER` | SQM | 20 | 15 | 3 |
| `STAIN_BLOCK_PRIMER` | SQM | 25 | 40 | 4 |
| `WALLPAPER_STRIPPING` | SQM | 48 | 2 | 14 |
| `DOOR_PAINT` | UNIT | 350 | 150 | 55 |
| `TRIM_PAINT` | UNIT | 140 | 52 | 22 |
| `RADIATOR_PAINT` | UNIT | 270 | 115 | 40 |
| `DOWNLIGHT_CUTTING` | UNIT | 46 | 0 | 8 |
| `CORNICE_CUTTING` | ROOM | 308 | 0 | 45 |
| `MASKING` | ROOM | 115 | 62 | 25 |
| `MOBILIZATION` | LUMP_SUM | 1900 | 0 | 60 |

Crew defaults: 3 people, 8 h/day, 4,500 TL/day.

> These are market-derived placeholders. Replace with the business's actual costs before launch.

Service districts: all 39 Istanbul districts, active, `district_factor = 1.0000` initially.

---

## 6. Vision Analysis

### Call structure

One call per room. All photos for that room go in the same context — this lets the model compare walls, keep tone consistent, and avoid double-counting a crack visible in two frames. Per-photo calls lose all of this.

- Photos are labelled (`WALL_1`, `CEILING`, `DETAIL_1`) so output IDs map back
- `DETAIL` photos go in the **same** call — separating them loses which wall the crack belongs to
- A 3+1 home = 7 parallel calls
- Job granularity is per room, so one failure retries one room

### Output schema

Enforce with structured output / JSON schema. Validate before persisting; on validation failure, retry once then fail the job.

```json
{
  "roomType": "LIVING_ROOM",
  "surfaces": [{
    "id": "WALL_1",
    "photoId": "uuid",
    "coating": "PAINTED",
    "currentTone": "DARK",
    "fillerRatio": "MEDIUM",
    "skimCoatRequired": false,
    "crackLevel": "HAIRLINE",
    "moisture": "NONE",
    "wallpaper": false,
    "confidence": 0.88
  }],
  "ceiling": {
    "cornice": true,
    "downlightCount": 6,
    "staining": "LIGHT",
    "fillerRatio": "LOW",
    "confidence": 0.79
  },
  "furnishing": "FURNISHED",
  "doorCount": 2,
  "windowCount": 3,
  "radiatorCount": 1,
  "confidence": 0.83,
  "unusablePhotos": [],
  "notes": ["sol duvarda priz hizasında çatlak"]
}
```

**`notes` must stay Turkish** — the operator reads it. State this explicitly in the prompt, otherwise the model translates the whole output to one language.

The model must never output square metres, prices, or totals.

### Confidence aggregation

Room confidence is the **weighted average** of surface confidences, not the minimum. One blurry frame should not poison an entire room.

### Cross-checks

| Check | Behaviour |
|---|---|
| `declaredDoors != detectedDoors` | Flag for operator. Detection does **not** silently override. |
| `declaredFurnishing != detectedFurnishing` | Ask the customer one question (below) |

Furnishing is categorically different: the photo shows the home *today*, the painting happens *later*. The model cannot observe the future state. The common case is a tenant getting a quote before moving out. Ask:

> Fotoğraflarda eşya görünüyor. Boya günü ev boş olacak mı?
> `[Evet, boş olacak]` `[Hayır, eşyalı kalacak]`

Abuse risk is handled by a quote condition, not by overriding the declaration.

### Decision thresholds (`ConfidenceEvaluator`)

```java
enum ReviewDecision { AUTO, RECAPTURE, SURVEY }
```

```
if (unusablePhotos not empty && recaptureCount == 0)  → RECAPTURE
if (riskFinding)                                      → SURVEY
if (overallConfidence < 0.65)                         → SURVEY
if (overallConfidence < 0.80
    && estimatedTotal > averageJobValue × surveyAmountFactor) → SURVEY
otherwise                                             → AUTO

riskFinding =
     any surface moisture == ACTIVE
  || any surface crackLevel == STRUCTURAL
  || skimCoatArea / totalWallArea > 0.40
  || any room missing required photos
```

`AUTO` still means `PENDING_REVIEW` — it is not auto-send. Automatic sending is a phase 3 decision.

**Recapture is requested once only.** The request must be specific: not "retake your photos" but "the second wall of the living room came out dark", with a link that opens that exact capture screen. A second failure goes to the operator.

When `SURVEY` is decided, show the customer the **stage 1 range**, not a new one derived from analysis you do not trust. Exception: if a risk finding triggered it, widen the range upward and state the reason — repeating the old range sets a false anchor.

---

## 7. API

Base: `https://api.<domain>` — same registrable domain as the web app so the session cookie works with `Domain=.<domain>; SameSite=Lax`. Fully separate domains would force `SameSite=None` and browser friction.

### Anonymous (signed httpOnly cookie bound to `quote_request.id`)

```
POST   /api/quote-requests                       create draft
PATCH  /api/quote-requests/{id}                  incremental update
POST   /api/quote-requests/{id}/estimate         stage 1 range
GET    /api/quote-requests/resume/{token}        QR / SMS handoff
POST   /api/quote-requests/{id}/rooms/confirm    accept derived room list
POST   /api/photos/upload-intent                 → presigned PUT + photo id
POST   /api/photos/{id}/complete                 mark uploaded
DELETE /api/photos/{id}                          retake
POST   /api/quote-requests/{id}/submit
POST   /api/otp/send
POST   /api/otp/verify
GET    /api/districts                            active service districts
```

### Verified (short-lived token issued after OTP)

```
GET    /api/quotes/{id}
POST   /api/quotes/{id}/accept                   body: { preferredSlot }
POST   /api/quotes/{id}/question
POST   /api/quote-requests/{id}/deletion-request
```

### Operator (Spring Security, separate realm)

```
GET    /api/op/queue?tab=PENDING|SURVEY|CALLBACK|SENT
GET    /api/op/quote-requests/{id}
POST   /api/op/quotes/{id}/approve
POST   /api/op/quotes/{id}/adjust                body: { lines[] }
POST   /api/op/quotes/{id}/convert-to-survey
POST   /api/op/quote-requests/{id}/cancel
GET    /api/op/callbacks
POST   /api/op/callbacks/{id}/resolve
POST   /api/op/job-outcomes
GET    /api/op/price-books
POST   /api/op/price-books                       new version (clone + edit)
POST   /api/op/price-books/{id}/bulk-increase    body: { target: LABOUR|MATERIAL|ALL, percent }
POST   /api/op/price-books/{id}/activate
GET    /api/op/deletion-requests
POST   /api/op/deletion-requests/{id}/approve
```

Operator responses include `total_cost` and `margin_ratio`. **These must never appear in customer-facing DTOs** — use separate response types, not conditional field stripping.

No bulk approve endpoint. It would eliminate the only human quality gate in the system.

---

## 8. Async Processing

No broker. PostgreSQL table + `@Scheduled` poller.

```sql
UPDATE analysis_job SET status = 'RUNNING', started_at = now(), attempts = attempts + 1
WHERE id IN (
  SELECT id FROM analysis_job
  WHERE status = 'PENDING' AND run_after <= now()
  ORDER BY id
  LIMIT 5
  FOR UPDATE SKIP LOCKED
)
RETURNING *;
```

`SKIP LOCKED` handles concurrency safely across instances.

Retry with exponential backoff (`run_after = now() + 2^attempts minutes`), `max_attempts = 3`, then `FAILED` and flag for the operator.

### Scheduled jobs

| Job | Frequency | Action |
|---|---|---|
| `AnalysisPoller` | 10 s | Claim and run analysis jobs |
| `QuoteExpiry` | hourly | `QUOTE_SENT` past `valid_until` → `CLOSED/EXPIRED` |
| `ExpiryReminder` | daily | Notify 3 days before `valid_until` |
| `CallbackOverdue` | hourly | Flag `callback_task` past SLA, notify operator |
| `PhotoPurge` | daily | Delete photos past `delete_after` from MinIO + mark row |
| `DeletionReminder` | daily | Nag operator about pending deletion requests |

### SLA windows

Customer-facing time promises must respect business hours. A request at 23:00 must not say "within 2 hours". Compute the promise against the configured working window and render accordingly ("yarın sabah 10:00'a kadar").

---

## 9. Storage

Photos never pass through the JVM. Client uploads directly to MinIO via presigned PUT.

- **Bucket CORS** must allow `PUT` and `OPTIONS` from the web origin — without this the capture flow breaks entirely
- **Presigned GET** for operator reads only, short TTL
- **Key format:** `quotes/{quoteRequestId}/{roomId}/{photoId}.jpg` — non-guessable via UUIDv7
- **ILM lifecycle rule** as a backstop to `PhotoPurge`; run both

Client-side pipeline before upload:

1. Read EXIF `DateTimeOriginal` → send as `capturedAt`
2. Draw to canvas, resize (long edge 2048 px; 2560 px for `DETAIL`)
3. Re-encode JPEG q85 — this strips EXIF as a side effect
4. Compute quality score (Laplacian variance) and reject obvious failures
5. Upload

**Keep rejection thresholds loose.** Rejecting a good photo is far more costly than accepting a mediocre one — the user gets annoyed and abandons. After 3 rejections of the same frame, accept it and set `low_quality_flag`.

---

## 10. Frontend (Nuxt)

### Route strategy

```
prerender  →  /
              /{district}-boya-badana-fiyatlari      (39 static pages)
              /nasil-calisir
ssr: false →  /teklif-al/**
              /cekim/**
              /teklifim/**
              /op/**
```

SEO pages matter because traffic is the real bottleneck in a self-serve funnel. District-level local search converts best in this sector. The capture flow gains nothing from SSR.

### Key behaviours

- **Draft persistence:** `PATCH` on every step. Do not rely on localStorage — people abandon mid-flow and resume on another device
- **Camera:** `<input type="file" accept="image/*" capture="environment">`. No native app needed; this opens the device camera on iOS Safari and Android Chrome
- **Upload queue:** background upload the moment a photo is confirmed, with progress indicator
- **Resume handoff:** QR and SMS links both hit `/api/quote-requests/resume/{token}`; desktop screen switches to a "continuing on your phone" state
- **State:** Pinia; typed API client generated from OpenAPI, CI fails on drift
- **Copy:** `@nuxtjs/i18n`, single `tr` locale, `strategy: 'no_prefix'` so URLs stay `/teklif-al`. Every string the customer reads comes from `web-ui/i18n/locales/tr.json`. Two places cannot read it and are duplicated deliberately: the PWA manifest (build-time metadata) and the district list (needed at prerender time, when the API is unreachable)
- **Do not proxy through Nuxt server routes** — call the API directly

---

## 11. Rate Limiting

Two separate mechanisms with different strictness.

**Analysis quota** — primary control on phone, loose on IP:

| Scope | Limit | On exceed |
|---|---|---|
| Phone | 2/day, 5/month | Queue with `QUOTA_EXCEEDED` flag, do not reject |
| IP | Generous | Flag only, never block |

Turkish mobile carriers use CGNAT — thousands of users share an exit IP. A strict IP quota blocks real customers.

**OTP sending** — strict, because every SMS costs money and this is the most attackable endpoint:

| Scope | Limit |
|---|---|
| Phone | 1/min, 5/day |
| IP | 10/hour (strict is fine — a legitimate user requests OTP once) |
| Failed attempts | Exponential backoff, lock after 5 |

---

## 12. Data Retention

| Data | Quote closed | Job won |
|---|---|---|
| Photos | Delete 30 days after **close** | Job completion + warranty period |
| Analysis results | Retain long-term | Retain long-term |
| Quote + line items | Statutory record period | Statutory record period |
| Phone number | Delete on request | Statutory record period |

Set `photo.delete_after = quote_request.closed_at + 30 days`. **Counting from send date rather than close date would delete photos while the customer is still deciding.**

**Delete the photo, keep the finding.** Calibration needs `surface_finding` rows, not images. This lowers both legal exposure and storage cost while preserving the improvement dataset.

Consent is versioned (`text_version`) so you know which notice each grant referred to. Revocation triggers immediate deletion.

Deletion approval screen must distinguish what goes and what stays:

```
Deleted:  photos · analysis records · phone number
Retained: quote totals and line items · job record  (statutory period)
```

**Backups:** photos must have a lifecycle separate from database backups. A 30-day deletion policy alongside 90-day backups is a policy that exists only on paper.

Daily `pg_dump` shipped **off the server**. The database is the irreplaceable asset — photos are ephemeral by design, but price book versions and calibration history cannot be regenerated.

---

## 13. Notifications

Eleven templates — seven customer-facing, four operator-facing (`is-akis-sureci.md` §9). All Turkish, all short.

| Code | Trigger | Content |
|---|---|---|
| `ESTIMATE_SMS` | Stage 1 SMS requested | Range + link to continue |
| `QUOTE_READY` | Operator approved | Link only — **no amount** |
| `RECAPTURE_NEEDED` | Analysis found unusable photos | Which frame + direct link |
| `SURVEY_NEEDED` | Survey decided | Reason + "we'll call you" |
| `EXPIRY_REMINDER` | 3 days before expiry | Expiry date + link |
| `QUOTE_EXPIRED` | Past `valid_until` | Link to start fresh |
| `ACCEPT_CONFIRMED` | Customer accepted | Callback promise + preferred slot |
| `OPERATOR_NEW_REQUEST` | New item in review queue | District, summary, estimate, link |
| `OPERATOR_QUOTE_ACCEPTED` | Customer accepted | District, amount, preferred call slot, link |
| `OPERATOR_CALLBACK_OVERDUE` | `callback_task` past SLA | How many hours it has waited, link |
| `OPERATOR_DELETION_REQUEST` | Deletion request awaiting approval | Link |

**`QUOTE_READY` must not contain the amount.** A bare number without the line-item breakdown gets judged out of context and the customer never opens the quote.

**Turkish characters double SMS cost.** A message containing ı, İ, ğ, Ğ, ş, Ş or lowercase ç drops to UCS-2 encoding: 70 characters per segment instead of 160. (ö, ü, Ö, Ü and uppercase Ç are inside GSM-7, so "strip the Turkish letters" is both wrong and ugly.)

Billing is per segment, so correctly spelled Turkish under 70 characters costs exactly what de-accented Turkish under 160 costs. Spell it properly and keep it short.

Templates live at `api/src/main/resources/notifications/tr/<TEMPLATE_CODE>.txt`. `SmsSegmentBudgetTest` measures each one *after* substituting realistic placeholder values — which is what goes over the wire — and fails the build when one outgrows its budget. `RECAPTURE_NEEDED` is budgeted at two segments on purpose: §6 requires it to name the frame that failed, and that does not fit in 70 characters.

Operator notification should be SMS or WhatsApp, not PWA push — push is unreliable on iOS and the operator will not keep the app open.

---

## 14. Deployment

```yaml
services:
  caddy:      # automatic TLS, reverse proxy
  web:        # Nuxt
  api:        # Spring Boot
  postgres:
  minio:
```

Secrets via `.env` with restricted file permissions. Do not introduce a secrets manager for a single VPS.

CI/CD: build → push image → SSH → `docker compose pull && up -d`. Flyway runs on application startup.

**Set a disk alarm.** The most likely failure mode on a single VPS is MinIO, Postgres WAL, and container logs filling the disk simultaneously. Configure log rotation and a usage threshold.

Single server is a single point of failure — acceptable here, but accept it deliberately. Enable provider snapshots weekly.

---

## 15. Scope

v1 delivers the `quoting` module plus a minimal `customer` module.

**`customer` (minimal):** identity by phone, created on OTP verification, contact details, repeat-customer lookup. Nothing else — no CRM features, no segmentation, no notes.

**`quoting`:** stage 1 form and instant range · desktop→mobile QR handoff · room list derivation and confirmation · guided photo capture with client-side quality checks · background upload · phone verification · per-room vision analysis · pricing engine with versioned price book · duration and minimum calculation · operator queue and review with four actions · structured adjustment logging · callback list · quote screen and acceptance · eleven SMS templates · out-of-service-area waitlist · consent records, automated deletion, deletion requests · district SEO pages

**Cross-cutting:** domain event publication per §2.4, even where no subscriber exists yet.

### Deferred

| Feature | Reason |
|---|---|
| Calendar / scheduling | Handled by phone; painting schedules resist software rigidity |
| Automatic quote sending | Requires calibration data first |
| Email channel | SMS sufficient for v1 |
| LiDAR measurement | Device-limited; would remove the m² question but not needed for v1 |
| Video capture | Photos superior on every axis |
| Calibration dashboards | Meaningless before data accumulates; `quote_adjustment` is captured in v1 |
| Multi-operator management | One operator |
| `scheduling`, `jobs`, `crew`, `procurement`, `invoicing`, `analytics` | Separate modules; `quoting` is built so they slot in without restructuring |

### Phases

| Phase | Content | Precondition |
|---|---|---|
| 0 | Populate price book with real costs; collect data from last 50 jobs | — |
| 1 | v1 scope; every quote passes operator review | Phase 0 |
| 2 | Calibration: compare estimates to `job_outcome`; tune coefficients | 20–30 completed jobs |
| 3 | Auto-send for high-confidence simple jobs | Phase 2 results |

**Phase 0 cannot be skipped.** Without extracting how the business actually prices, the rest is guesswork.

### Delivery increments

The phases above are about *confidence* — what has to be true before the next step is safe. Delivery order is a separate axis, set by the workflow document (`docs/product/is-akis-sureci.md` §12). Both apply; neither replaces the other.

| Increment | What ships | What it buys |
|---|---|---|
| 1 | Pricing engine and price book management — **an internal tool, no customer interface** | Figures can be entered by hand and a quote computed. The riskiest assumption is tested first. |
| 2 | Stage 1 and the district pages | The site goes live, demand volume and traffic data start accumulating. No photos, no analysis. |
| 3 | Stage 2, analysis, and the operator panel | Quoting without a site visit begins. This is where the main benefit arrives. |
| 4 | Notifications, the callback list, automated deletion | Manual follow-up becomes automatic. |

Each increment is useful on its own and does not wait for the next.

**Increment 1 having no customer interface is the point.** The riskiest assumption in the system is whether the pricing engine produces figures that match what the business would actually charge. Testing that needs a price book, an engine and a way to enter inputs — not a website. Discovering the engine is wrong after stage 1 and the capture flow exist means throwing away work that was built on a wrong number.

This also lines up with §17: `PricingEngine` is the first thing to be built and the first thing to be tested, and increment 1 is the smallest shippable thing that contains it.

---

## 16. Configuration Requiring External Input

| Item | Source |
|---|---|
| Real cost figures for all price book items | Business |
| Target margin and minimum threshold | Business |
| Crew size and daily cost | Business |
| District factors | Business |
| Quote validity period | Business |
| Working hours / SLA window | Business |
| `labour_vat_rate`, `material_vat_rate` | **Accountant** — legislation treats labour and materials differently for residential maintenance and painting services |
| Privacy notice and consent text, `text_version` | **Legal counsel** |
| Commercial vs informational message classification, İYS registration | **Legal counsel** |
| Quote terms (furnishing surcharge clause, revision conditions) | **Legal counsel** |
| SMS provider and sender ID registration | Business — registration takes time, start early |
| Vision provider and data processing agreement | Business |

---

## 17. Testing Priorities

Development is test-first; see `docs/decisions/0009`. The order below is therefore also the order in
which the code gets written, not a list of tests to add afterwards.

1. **`PricingEngine`** — highest priority. Pure class, no infrastructure. Cover: the worked example in §5.10 as a regression fixture, each modifier in isolation, modifier ordering, minimum-binding cases, band width for every uncertainty combination, and rounding behaviour.
2. **`RoomListDeriver`** — every layout × scope combination.
3. **`ConfidenceEvaluator`** — each threshold branch and every risk finding.
4. **State machine** — all valid transitions accepted, all invalid ones rejected.
5. **Analysis schema validation** — malformed model output must fail cleanly and retry, never persist partial data.

Frontend units are driven the same way with Vitest, specs beside the code. `web-ui/app/utils/districts.spec.ts` is the pattern for a particular case worth repeating: where two sources of truth are deliberately duplicated, a test parses one and asserts the other still matches it, so the drift is caught by the build rather than by a customer hitting a form that cannot submit.

Use a fake `VisionAnalysisPort` throughout. A test suite that makes real vision calls is unusable.
