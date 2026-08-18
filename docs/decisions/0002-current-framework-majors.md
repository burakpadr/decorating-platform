# 2. Build on the current framework majors, not the ones the spec names

Date: 2026-08-16
Status: accepted

## Context

The specification's stack table named Spring Boot 3.x and Nuxt 3, which were current when it was
written. By the time scaffolding began:

- Spring Initializr no longer offers a 3.x line at all, and Boot 3.5's OSS support window has closed.
  Starting a greenfield project on it means starting on an unsupported line.
- Nuxt 3 is in maintenance; Nuxt 4 is the current major.
- `springdoc-openapi` 3.1.0 is GA and supports Boot 4, so the contract pipeline — the main reason
  this is a monorepo — is not a blocker.

## Decision

Spring Boot 4.1 and Nuxt 4.5. Java stays at 21: that is the LTS the toolchain pins, not a consequence
of the Boot version.

The spec's stack table was updated to the versions actually installed, with a paragraph recording why
it changed.

## Consequences

Boot 4 moved autoconfiguration classes into new packages — the `openapi` Spring profile excludes
`org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration` and its siblings under
their Boot 4 names, which will look unfamiliar to anyone who knows the 3.x names.

Anyone reading an older copy of the spec will find version numbers that no longer match. The stack
table in the current spec is authoritative; this record explains the gap.
