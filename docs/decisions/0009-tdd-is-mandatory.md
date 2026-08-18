# 9. TDD is mandatory

Date: 2026-08-18
Status: accepted

## Context

The skeleton was built without TDD. That was consistent with its scope — configuration, schema and
enforcement rules, no business logic — but the next thing to be built is `PricingEngine`, and that
changes the calculus completely.

The platform's value is one deterministic calculation: observations plus declared measurements in,
money out. An arithmetic error there has no user-visible symptom. It does not throw, it does not log,
it does not fail a health check. It quietly misprices every quote it touches, and the business finds
out through margin drift months later — after `job_outcome` rows have accumulated against wrong
estimates, which also poisons the calibration dataset those rows exist to feed.

A test written after the implementation asserts what the implementation does. If the implementation
applied the furnishing surcharge to materials as well as labour — the specific mistake §5.7 warns
about — a test written afterwards would lock that in and go green.

## Decision

TDD, without exceptions: failing test first, observed failing, then the implementation.

Two habits are part of it:

- **Prove a rule in both directions.** Break it deliberately, confirm the build fails, restore it. A
  rule nobody has watched fail is a rule nobody knows works.
- **Never report a test as passing without running it.**

Enabling work done alongside this decision, because the rule was otherwise impossible to follow on one
side: Vitest wired into `web-ui` and `api-client`, `pnpm -r test` added to the web CI pipeline,
`make watch-web` and `make test-one TEST=…` added for the loop itself.

## Consequences

The frontend default test environment is plain `node`, not `nuxt`. A Nuxt runtime in front of every
assertion makes the loop too slow to write tests first, which would quietly kill the practice; tests
that need it opt in per file. The measured loop is around 200 ms.

`PricingEngine`'s purity rule (§2, enforced by `ArchitectureRulesTest`) is now doing double duty. It
was justified as an architectural boundary; it is also what keeps the domain suite free of Spring and
Postgres, and therefore fast enough to drive.

The spec becomes the test plan rather than a document read once. §5.10 is the regression fixture, §5.7
is a case per modifier, §5.9 is a case per uncertainty combination, §17 is the order. Where the spec is
ambiguous, that ambiguity now surfaces as an unwritable test — which is the useful moment to ask rather
than guess.

Cost: the skeleton's own code was not built this way and is not retrofitted. `SecurityConfig`, the
event records and the SQL have no unit tests, and adding them after the fact would be the exact
anti-pattern this record rejects. They get tests when behaviour is added to them.
