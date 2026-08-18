# 11. Past jobs are recorded separately from quote outcomes

Date: 2026-08-18
Status: accepted

## Context

BOYA-2 is the second half of Phase 0: the record of the last 50 jobs — ev tipi, m², yapılan işler,
verilen fiyat, gerçekleşen maliyet (workflow §12). Its acceptance criterion is that the price list is
validated against this data and the coefficients get their first calibration.

The data itself has to come from the business and has not arrived. What could be decided now is where
it lands, because that turned out not to be obvious.

**`job_outcome` cannot hold it.** That table records stage 8 against a quote the system produced, so
`quote_request_id` is `NOT NULL` and a foreign key. Jobs finished before the system existed have no
quote request and never will.

Relaxing that column would have been the smaller diff and the worse decision. `job_outcome` is
precisely the input Phase 2 compares the engine's estimates against (spec §15). A nullable link would
put rows the engine priced and rows it never saw in one table, distinguished only by a NULL — and that
distinction *is* Phase 2. Every query would have to remember it; one that forgot would report the
engine's accuracy against jobs the engine never touched.

## Decision

`historical_job` and `historical_job_item`, in `V4__historical_job.sql`, hold the pre-system record.
Separate from `job_outcome`, with no link to `quote_request`.

Three things follow from what the data is for, and each is a constraint rather than a convention:

- **`job_ref` is unique.** The business's own reference for the job. Calibration is arithmetic over
  rows nobody reads individually, so a job imported twice is not a visible duplicate — it is a moved
  average. Re-running an import has to fail, not accumulate.
- **A row with neither gross nor net m² is rejected.** Every figure this data produces is per m². Such
  a row is not a weak data point; it is one that silently drags whatever it is averaged into.
- **A labour/material split must reconcile with the total.** The `FURNISHED` surcharge applies to
  labour only (§5.7), so the two calibrate separately, and a split that does not add up is a
  transcription error that survives review.

**No personal data.** These rows are never deleted — they are the baseline every later calibration is
measured from — so they outlive every retention window in §12. That is defensible only while the
record is about a job rather than about a person: a district, and no name, phone or address.
`HistoricalJobSchemaTest` fails the build if such a column appears.

**Work items carry no unit.** The unit belongs to the code and already lives in `price_book_item`. A
second copy could only ever contradict the first, and a quantity recorded against the wrong unit is
the kind of error that reads as plausible and comes out as a wrong average.

**Unknown work codes are reported, not rejected.** The ledger is allowed to contain work this system
does not price — floors, plasterboard, electrics. The import is not the place to decide that.
`historical_job_unknown_item_code` lists them, and it has to be read before any per-code comparison,
because such a code compares against nothing while appearing to be included.

The comparison lives in `historical_job_calibration`, a view: cost and quote per m², realised margin
against the active book's `margin_ratio`, implied crew day cost, implied gross-to-net ratio, and quote
accuracy where the invoice was recorded. A view rather than application code because there is no
application code yet — increment 1 starts at `PricingEngine` (§17) — and because what BOYA-2 asks for
is arithmetic over evidence, not a feature.

Intake is two CSV templates plus `import.sql`, in `api/src/main/resources/calibration/`, with the
Turkish column sheet at `docs/product/son-50-is-kaydi.md`. The templates' headers are the tables'
columns, asserted by the test, as is the sheet documenting every column: a renamed column that reaches
the business as an unexplained header comes back empty.

## Consequences

**BOYA-2's acceptance criterion is not met by this.** The apparatus is in place; the price list is
validated and the coefficients calibrated when the 50 records are imported, and not before. The card
belongs on the board as waiting on the business, not as done — the same distinction ADR 0010 drew for
`REAL-2026-01`, whose figures are still market research.

**Margin is measured against the invoice, not the quote.** Where `actual_total_ex_vat` was recorded
and differs, that is what realised margin uses. A job that ran over and was invoiced higher earned
more than its quote implies; measuring the price list against the offer would credit it with accuracy
it did not have.

**All money is ex-VAT.** Both VAT rates are still placeholders (BOYA-3), so a VAT-inclusive figure
could not be split here without inventing the rate it was charged at. The business does that
subtraction once, at intake.

**Areas recorded gross are derived with `gross_to_net_ratio`** — itself uncalibrated — so
`net_area_estimated` travels with the row rather than being resolved into it.

This view cannot reprice a historical job through the engine: that needs the engine, plus the room
list and per-room measurements these records do not carry. Coefficients that depend on room geometry —
ceiling height, perimeter factors, opening ratios — are therefore out of reach here and stay Phase 2
work, against `job_outcome`.
