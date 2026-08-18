# 5. Domain events are the only module seam, and the test says so

Date: 2026-08-18
Status: accepted

## Context

Spec §2 rule 3 says a module may depend on `shared` and on other modules' *published events* —
nothing else. `ArchitectureRulesTest` enforced only the first half: it forbade **every** dependency
between `quoting` and `customer`, and no `event` package existed for the exception to point at.

So the rule as written would have failed the build on the very integration the spec mandates. The
predictable outcome: the first time `scheduling` subscribes to `QuoteAccepted`, someone deletes the
rule to get green, and the guardrail is gone entirely rather than narrowed.

`outbox`, which §2.4 requires for events whose loss must not be silent, was also missing from the
schema — §4 never listed it.

## Decision

Each module publishes from its own `domain/event` package, and that package is the only one another
module may import. `shared.DomainEvent` is the base contract. The six events of §2.4 exist now, before
anything subscribes.

Two rules enforce it, both generated from a `MODULES` list so adding a module is one line:

- a module may reach another module's `domain.event` package and nothing else
- an event may reach `shared` and primitives and nothing else — it cannot carry
  `quoting.domain.model.Quote` and hand a subscriber the whole module

`outbox` was added to the V1 baseline, claimed the same way as `analysis_job` with
`FOR UPDATE SKIP LOCKED`.

## Consequences

Events carry IDs, not objects. A subscriber that needs more calls back through the publishing module's
inbound ports. This is the constraint that keeps the seam thin, and it will feel restrictive the first
time a subscriber wants a field it cannot see.

`DomainEvent.eventType()` returns a stable constant rather than the class name, so renaming a class
does not orphan rows already sitting in the outbox.

Both rules were verified by deliberately violating them and watching the build fail, then reverting.
A rule nobody has seen fail is a rule nobody knows works.
