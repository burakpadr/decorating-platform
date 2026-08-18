# 6. Vision prompts and SMS templates are versioned resources, not database rows

Date: 2026-08-18
Status: accepted

## Context

Two kinds of text had nowhere to live. `api/src/main/resources` contained only `db/migration`.

- The vision prompt. Spec §6 makes `room_analysis.prompt_version` mandatory, because results produced
  by different prompts are not comparable and the calibration dataset depends on knowing which prompt
  produced which finding.
- The eight SMS templates of §13. `notification.template_code` records which template was sent; the
  wording itself had no home.

Without a home, both end up as string literals in the classes that use them, and versioning becomes
impossible after the fact.

## Decision

Both live in `src/main/resources`, in the deployed artifact:

- `prompts/room-analysis/<version>.md`, with the response schema beside it as `schema.json`. The
  filename **is** `prompt_version`.
- `notifications/<lang>/<TEMPLATE_CODE>.txt`.

A released prompt version is never edited in place. A prompt is an input to persisted analysis results
in exactly the way a price book version is an input to persisted quotes — editing one that existing
rows reference rewrites history silently. Add the next version instead.

## Consequences

A rollback of the application rolls the text back with it, and a diff shows what changed in a prompt
between versions. Neither is true of database rows.

The operator cannot edit either through the UI. That is the trade: for a price book the business needs
to change figures without a deploy, which is why it lives in the database; for a prompt, changing it
without a deploy is precisely the thing to prevent.

SMS wording is drafted, not final. §16 puts the copy, the commercial-versus-informational
classification and İYS registration with legal counsel.
