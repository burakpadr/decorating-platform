# Calibration intake

The record of the last 50 jobs — work item BOYA-2, the second half of Phase 0 (workflow §12,
spec §15). Two CSV templates, one import script, and the two views that read them.

The business-facing sheet, in Turkish, is `../../../../../docs/product/son-50-is-kaydi.md`: what every
column means, which are mandatory, and the work-code list. Read that before this. The reasoning behind
the schema is in `docs/decisions/0011`.

| File | What it is |
|---|---|
| `historical-jobs-template.csv` | Header only. One row per completed job. Header order **is** the column order of `historical_job`. |
| `historical-job-items-template.csv` | Header only. `job_ref` plus the work items of that job. |
| `import.sql` | Loads both files in one transaction, then prints the reports. |

The templates carry no example row on purpose: an example in the file being filled in gets imported
as a job sooner or later. The filled example lives in the Turkish sheet instead.

`HistoricalJobSchemaTest` asserts that both headers still match the tables they load into and that
the Turkish sheet documents every column. Adding a column without telling the business about it, or
renaming one without updating the template, fails the build — the drift is caught here rather than by
an import that silently leaves a column empty.

## Why CSV and not an operator screen

There is no operator screen yet. Increment 1 starts at `PricingEngine` (§17), and this data is needed
*before* it, to say whether the engine's figures are the business's figures. A one-time import from a
spreadsheet is also what the source actually is: a ledger, not a form somebody will fill in fifty
times.

## Importing

The two filled-in files must be named `historical-jobs.csv` and `historical-job-items.csv`, and psql
must be started from the directory holding them — `\copy` runs client-side and does not interpolate
psql variables, so the names are fixed in the script.

```sh
make -C ../../../../.. infra                     # Postgres, if it is not already up
cd <directory with the two filled-in files>
psql "$DATABASE_URL" -f <repo>/api/src/main/resources/calibration/import.sql
```

If the work items have not been collected yet, copy the template's header line into an otherwise
empty `historical-job-items.csv`; the job rows import on their own. Everything happens in one
transaction, so a row the constraints reject leaves the table as it was — nothing half-imported for a
second run to double-count.

Three failures the import will not let past:

- a duplicate `job_ref` — the same job counted twice moves every average
- a work row whose `job_ref` matches no job — rejected rather than dropped by a join
- a labour/material split that does not add up to `actual_cost`

## Reading the result

`import.sql` prints the reports at the end; both views can be queried again at any time.

- **`historical_job_unknown_item_code`** — read it first. A work code with no row in the active price
  book compares against nothing and drops out of any per-code comparison without saying so.
- **`historical_job_calibration`** — one row per job against the active book: cost and quote per m²,
  realised margin and its gap to `margin_ratio`, implied crew day cost, implied gross-to-net ratio,
  and quote accuracy where the invoice was recorded. Empty until the import runs.

## What this cannot do yet

It cannot reprice a historical job through the pricing engine. That needs the engine — which does not
exist — plus the room list and per-room measurements these records do not carry. The comparison here
is at total and per-m² level, which is what validates the price list and what the derivable
coefficients come from.

Repricing estimate against outcome is Phase 2 (spec §15), against `job_outcome` rows the engine
actually priced. These rows are the baseline that pass is measured from, which is also why they are
never deleted, and therefore why they hold no personal data.
