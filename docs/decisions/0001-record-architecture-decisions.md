# 1. Record architecture decisions

Date: 2026-08-16
Status: accepted

## Context

The implementation specification says *what* to build. It does not say why the build deviates from it
where it does, and several such deviations were decided within days of starting: the framework major
versions, the backend build tool, the directory layout, where generated and versioned assets live.

Left in commit messages and chat logs, those reasons disappear. The predictable failure is someone
reading the spec, seeing the code disagree, and "fixing" the code back — undoing a decision whose
reasoning they never saw.

## Decision

Keep short decision records in `docs/decisions/`, numbered and dated, in the format used here:
context, decision, consequences.

One record per decision that a future reader could otherwise reverse by accident. Not one per commit,
and not one per preference — a record earns its place only when it explains something the code cannot.

A record is immutable once accepted. Superseding one means adding a new record that says so, never
editing the old one; the point is the trail, not the current state. Current state lives in the spec,
the README and the two `CLAUDE.md` files.

## Consequences

Anything here that contradicts the spec is a deliberate divergence with its reasoning attached, and
the spec is updated to match. A divergence with no record is a bug in one of the two.
