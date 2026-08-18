# Calibration intake

The record of completed jobs — what validates the price list and eventually calibrates the
coefficients (workflow §12, spec §15). The business has no job history to extract, so the record is
built forward: one row per job as it finishes (`docs/decisions/0012`).

The business-facing sheet, in Turkish, is `../../../../../docs/product/tamamlanan-is-kaydi.md`: what
every column means, which are mandatory, the work-code list, and when to fill it in. Read that before
this. Why the rows are here and not in `job_outcome` is `docs/decisions/0011`.

| File | What it is |
|---|---|
| `historical-jobs-template.csv` | Header only. One row per completed job. Header order **is** the column order of `historical_job`. |
| `historical-job-items-template.csv` | Header only. `job_ref` plus the work items of that job. |
| `import.sql` | Driver: opens a transaction, `\copy`s both files into staging, merges, reports. |
| `staging.sql` | The staging DDL — the CSVs verbatim, before any constraint applies. |
| `merge.sql` | Staging → `historical_job`. Everything that decides what enters the dataset. |

`import.sql` is the only file with psql meta-commands in it. That split is deliberate: `\copy` cannot
run over JDBC, so keeping it in the driver leaves the merge testable, and `HistoricalJobImportTest`
covers it.

The templates carry no example row on purpose: an example in the file being filled in gets imported as
a job sooner or later. The filled example lives in the Turkish sheet instead.

`HistoricalJobSchemaTest` asserts that both headers still match the tables they load into and that the
Turkish sheet documents every column. Adding a column without telling the business about it, or
renaming one without updating the template, fails the build — the drift is caught here rather than by
an import that silently leaves a column empty.

## Why CSV and not an operator screen

There is no operator screen yet. Increment 1 starts at `PricingEngine` (§17), and this data is what
says whether the engine's figures are the business's figures. A spreadsheet is also what the source
actually is.

## Importing

The filled-in files must be named `historical-jobs.csv` and `historical-job-items.csv`, and psql must
be started from the directory holding them — `\copy` runs client-side and does not interpolate psql
variables, so the names are fixed in the script. `\ir` pulls in `staging.sql` and `merge.sql` from
beside `import.sql`, wherever that is.

```sh
make -C ../../../../.. infra                     # Postgres, if it is not already up
cd <directory with the two filled-in files>
psql "$DATABASE_URL" -f <repo>/api/src/main/resources/calibration/import.sql
```

If no work items have been collected, copy the template's header line into an otherwise empty
`historical-job-items.csv`; the job rows import on their own.

**It is meant to be run again.** Jobs are recorded as they finish, so the file will routinely contain
jobs already imported. Those are skipped, listed by `job_ref` in the output, and **not overwritten** —
a re-typed figure does not silently replace a recorded one. Correcting a job already in the dataset is
a deliberate `UPDATE`. A work code missed on an earlier run does still land, because items conflict per
(job, code) rather than per job.

Everything happens in one transaction. Two things reject the whole batch rather than entering it
half-formed:

- a work row whose `job_ref` matches no job — rejected, never dropped by a join
- a labour/material split that does not add up to `actual_cost`

## Reading the result

`import.sql` prints the reports at the end; both views can be queried again at any time.

- **`historical_job_unknown_item_code`** — read it first. A work code with no row in the active price
  book compares against nothing and drops out of any per-code comparison without saying so.
- **`historical_job_calibration`** — one row per job against the active book: cost and quote per m²,
  realised margin and its gap to `margin_ratio`, implied crew day cost, implied gross-to-net ratio, and
  quote accuracy where the invoice was recorded.

Cost per m² says something from the first few jobs. Margin and the coefficients are distribution
questions, so they need the 20–30 jobs spec §15 puts the Phase 2 gate at. An empty or thin
`historical_job_calibration` is not validation.

## What this cannot do

It cannot reprice a job through the pricing engine. That needs the engine — which does not exist — plus
the room list and per-room measurements these records do not carry. The comparison here is at total and
per-m² level, which is what validates the cost list and what the derivable coefficients come from.

Repricing estimate against outcome is Phase 2, against `job_outcome` rows the engine actually priced.
These rows are the baseline that pass is measured from, which is also why they are never deleted, and
therefore why they hold no personal data.
