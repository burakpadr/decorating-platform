# 12. There is no job history, so the record is built forward

Date: 2026-08-19
Status: accepted

Amends the consequences of [0011](0011-past-jobs-are-recorded-separately-from-quote-outcomes.md).

## Context

BOYA-2 asked for the record of the last 50 jobs, and ADR 0011 built the place for it to land while
noting that the data itself had not arrived.

It is not going to arrive. The business keeps no structured record of completed jobs — not a partial
one, not one in a spreadsheet somewhere. There is nothing to extract.

That is not a delayed dependency, and treating it as one is the actual danger: a card sitting in
"blocked" reads as *coming soon*, and Phase 0's own stated precondition for Phase 1 ("Phase 0",
spec §15) would quietly never be satisfied while the build proceeded as if it had been.

The purpose behind the card does not go away with the data. `REAL-2026-01`'s figures are market
research (ADR 0010), and the riskiest assumption in the system is whether the engine's numbers are
numbers this business would charge (§15, workflow §12). Something still has to answer that.

## Decision

The dataset is built forward instead of backward. Every job completed from now on is recorded as it
finishes, into the tables V4 already created; there is no retroactive collection.

The tables need no change: `historical_job` means *a job the engine did not price*, which is what
every job will be until increment 3 quotes for real. After that, jobs the engine priced go to
`job_outcome`, which is the distinction 0011 exists to protect. The intake templates keep their
`historical-` names for the same reason — they match the table, and matching the table matters more
than reading naturally for a job that finished last week.

**Validation starts at the first recorded job, not the thirtieth.** The two questions have different
sample sizes, and conflating them is what makes people wait:

- *Is the cost list right?* Realised cost per m² against the book's figures. A handful of jobs already
  says whether `WALL_PAINT` at 62 + 38 TL/m² is in the right region, and whether `crew_day_cost` of
  4,500 is anywhere near `actual_labour_cost / actual_days`. The first jobs recorded are jobs the owner
  priced by hand, so they measure the cost list without the engine being involved at all.
- *Are the margin and the coefficients right?* Distribution, not average, so 20–30 jobs — which is
  where spec §15 already put the Phase 2 gate. It stays there.

BOYA-2 is closed as impossible as written, and replaced by a standing routine: fill one row per job as
it finishes. `docs/product/tamamlanan-is-kaydi.md` is that routine's sheet.

## Consequences

**Increment 1 ships with an unvalidated price list, knowingly.** Three things carry that risk, and
they are all already in the design rather than added for this: increment 1 has no customer interface,
so its first weeks are the owner pricing by hand and comparing; every quote in Phase 1 passes operator
review, the single human control point (workflow §11); and `margin_alert_threshold` flags a quote whose
margin falls under 0.20 before it is sent. What is *not* mitigated is a cost list wrong in the same
direction everywhere — that is exactly what the accumulating record is for, and until it accumulates,
nobody may read an empty `historical_job_calibration` as validation.

**Phase 1 no longer waits on Phase 0's second half.** Recorded here so that nobody restores the
precondition later on the strength of the spec's table. Phase 0's first half — BOYA-1, real item
costs — is unaffected and still owed; it is now the only route to figures that are not market research
before jobs accumulate.

**The import is recurring, not a one-time handover.** That changed its requirements: the file will
routinely contain jobs already imported, so `merge.sql` skips those and reports them rather than
overwriting a recorded job with a re-typed row, and a work row whose `job_ref` matches nothing still
rejects the batch. The staging DDL and the merge live in their own files so that everything deciding
what enters the dataset is under `HistoricalJobImportTest`; only the two `\copy` lines, which are psql
meta-commands, are not.

**Calibration now moves at the rate jobs complete.** Phase 3 (auto-send) depends on Phase 2, which
depends on 20–30 recorded jobs, so its distance is set by the business's job rate and not by
engineering. If that rate makes the wait unacceptable, the alternatives are elicitation — the owner
prices a set of synthetic jobs and the engine is tested against his numbers — or reconstructing
partial rows from invoices and supplier records. Both were considered and neither was taken; recording
them here so the choice is visible if the wait becomes the problem.
