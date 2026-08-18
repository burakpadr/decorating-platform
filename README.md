# Decorating Platform

Business operations platform for a painting and decorating company. v1 delivers the `quoting`
module plus a minimal `customer` module: a homeowner gets a painting quote without an on-site
visit.

**Core principle: vision analysis produces observations, never prices.** A deterministic pricing
engine computes money from those observations plus declared measurements.

Three documents, none of which replaces another:

- [`docs/engineering/implementation-spec.md`](docs/engineering/implementation-spec.md) — schema,
  contracts, algorithms. Authoritative for anything technical.
- [`docs/product/is-akis-sureci.md`](docs/product/is-akis-sureci.md) — the end-to-end business
  process (Turkish): Aşama 0–8, roles, decision points, exception paths, what the customer sees.
  Read it before implementing a flow.
- [`docs/decisions/`](docs/decisions/) — why the build diverges from the spec where it does. Read the
  relevant one before correcting something back toward the spec.

**Language rule:** all code, tables, columns, enums and API paths are English. Customer-facing copy
is Turkish and lives in the i18n layer only — never in enum values or column names.

## Layout

```
decorating-platform/
├── api/                     Spring Boot, hexagonal architecture (Maven)
├── api-client/              TypeScript client generated from OpenAPI
├── web-ui/                  Nuxt, PWA
├── docs/
│   ├── engineering/         implementation specification, deployment guide
│   ├── decisions/           architecture decision records
│   └── product/             business process + design rationale (Turkish)
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

`api-client` is a top-level sibling rather than a child of either app: `api` produces it and `web-ui`
consumes it, so it belongs to neither. That also keeps the backend build from writing inside the
frontend directory.

Nothing else is foldered, because every remaining root file is one its tooling only finds at the repo
root — pnpm resolves the workspace from `package.json` + `pnpm-workspace.yaml` + `pnpm-lock.yaml`,
editors walk *up* to `.editorconfig` so nesting it would hide it from `api/src`, and both `make` and
GitHub's README rendering start from the root.

Maven owns `api` independently; a pnpm workspace covers `api-client` and `web-ui`. The two
build systems are deliberately not unified — the coupling costs more than it returns. The Makefile
is the common entry point.

Java package root: `com.burakpadr.decorating`.

## Getting started

Needs Java 21, Node 22, pnpm (via `corepack enable pnpm`) and Docker.

```sh
make install       # workspace dependencies
make infra         # Postgres 17 and MinIO in Docker
make dev-api       # http://localhost:8080  (Flyway migrates on startup)
make dev-web       # http://localhost:3000
```

`make help` lists every target. Deployment is documented in
[`docs/engineering/deployment.md`](docs/engineering/deployment.md).

## The contract

The main payoff of the monorepo:

```
springdoc-openapi  ──►  api-client/openapi.json  ──►  src/schema.d.ts
```

Both files are committed. `make client` regenerates them; CI regenerates them too and fails on a
diff, so a backend DTO change that breaks the frontend is caught in the pull request that causes it
rather than at runtime.

CI uses path filters — `api-client` triggers both pipelines, because a contract change
must verify against both sides.

## Architecture rules

Four rules, enforced by `ArchitectureRulesTest` rather than by convention, because retrofitting any
of them is expensive:

1. `domain/` contains no framework annotations. JPA entities are separate classes in
   `adapter/out/persistence`, mapped explicitly.
2. `PricingEngine` has zero dependencies and is unit-testable without Spring or a database. This is
   the most important testability requirement in the system.
3. Modules see each other only through published events — a module may import another module's
   `domain/event` package and `shared`, nothing else.
4. Cross-module references use IDs, not objects. `quote_request.customer_id` is a plain `uuid` with
   no foreign key; a database-level FK would let a future module quietly join across the boundary
   and the separation would erode within a month.

A fifth rule falls out of the third: an event may carry IDs and `shared` value objects only, so it
cannot drag its own module across the boundary.

Modules integrate through domain events only. The six events of §2.4 exist already, under
`<module>/domain/event` — publish them from day one even where nothing subscribes, because adding the
publisher later means touching `quoting` again.

## Status

Structural skeleton. The schema, the contract pipeline, the deployment topology, the architectural
rules, the event seam, the i18n layer and the versioned prompt and SMS-template homes are in place;
the domain is not implemented yet.

Build order from here follows the spec's testing priorities: `PricingEngine` first (§5, §17), then
`RoomListDeriver`, `ConfidenceEvaluator`, and the state machine.

**Phase 0 cannot be skipped.** The seeded price book holds market-derived placeholders, not this
business's costs — see the header of `V2__seed_price_book.sql` for everything that needs real
figures, including the two VAT rates that require an accountant.
