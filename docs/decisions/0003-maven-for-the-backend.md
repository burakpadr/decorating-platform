# 3. Maven for the backend build

Date: 2026-08-18
Status: accepted

## Context

The backend was scaffolded with Gradle and the Kotlin DSL. Maven was requested instead.

The one part that did not port mechanically was contract generation. `springdoc-openapi` serves the
spec from a *running* application, so exporting it means starting the app, fetching `/v3/api-docs` and
writing the result. Under Gradle that was a custom task; Maven has no equivalent of an ad-hoc task, so
it had to become lifecycle phases.

## Decision

Maven 3.9 via the wrapper, `spring-boot-starter-parent` as the parent POM.

Contract generation lives in an `openapi` profile that chains `spring-boot:start` →
`springdoc-openapi:generate` → `spring-boot:stop` around the `integration-test` phase. `make client`
invokes `./mvnw -Popenapi verify -DskipTests`.

## Consequences

Three things about that profile are load-bearing and easy to undo by accident:

- **It stays a profile.** Bound to the default lifecycle, every `mvn verify` would start the
  application.
- **`jmxPort` is 18081.** `spring-boot:start` uses JMX to detect readiness and defaults to 9001 —
  which MinIO's console occupies in `infra/docker-compose.dev.yml`. The default fails with a JMX
  timeout whenever local infrastructure is up, and the error names neither MinIO nor the port
  collision.
- **Pretty printing moved into the application.** The Gradle task formatted the JSON itself; the Maven
  plugin writes the response body verbatim. `springdoc.writer-with-default-pretty-printer` in the
  `openapi` profile does it instead. Without it the committed `openapi.json` is one line and the
  fail-on-diff check becomes unreadable.

Gradle's up-to-date checking is gone, which removes the trap it created: the old task declared the jar
as an input specifically because a task with outputs and no inputs is considered up to date and ships a
stale contract.
