-- Load completed jobs into the calibration record, then report on them.
--
-- Jobs are recorded as they finish rather than extracted from history (ADR 0012), so this runs again
-- every time a batch is added, and the file may contain jobs already imported. Those are reported and
-- left alone — see merge.sql. Correcting a job already recorded is a deliberate UPDATE, not a
-- re-import.
--
-- The filled-in files must sit in the directory psql is started from, under exactly these names:
--
--   historical-jobs.csv
--   historical-job-items.csv
--
--   cd <the directory holding the two filled-in files>
--   psql "$DATABASE_URL" -f <repo>/api/src/main/resources/calibration/import.sql
--
-- Fixed names rather than psql variables because \copy does not interpolate them, and a server-side
-- COPY would need the files readable by the Postgres process itself. If no work items have been
-- collected, put the template's header line in an otherwise empty historical-job-items.csv.
--
-- One transaction: either the whole batch lands or none of it does, so a rejected row never leaves a
-- half-imported set behind that the next run would then double.

\set ON_ERROR_STOP on
\timing off

BEGIN;

-- \ir resolves beside this file, not beside the CSVs psql was started from.
\ir staging.sql

\copy staging_job FROM 'historical-jobs.csv' WITH (FORMAT csv, HEADER true)
\copy staging_item FROM 'historical-job-items.csv' WITH (FORMAT csv, HEADER true)

\ir merge.sql

\echo ''
\echo '=== Already recorded, left untouched (correct these with UPDATE if the figures changed) ==='
SELECT job_ref FROM import_skipped ORDER BY job_ref;

DROP TABLE staging_job, staging_item, import_skipped;

COMMIT;

\echo ''
\echo '=== The dataset now ==='
SELECT count(*) AS jobs, min(completed_on) AS earliest, max(completed_on) AS latest
FROM historical_job;

\echo ''
\echo '=== Work codes the active price book has no row for (read this first) ==='
SELECT * FROM historical_job_unknown_item_code ORDER BY code, job_ref;

\echo ''
\echo '=== Realised margin against the active price book ==='
\echo '(20-30 jobs before this means anything — spec §15 Phase 2)'
SELECT
  count(*)                                        AS jobs,
  round(avg(realised_margin_ratio), 4)            AS avg_realised_margin,
  min(realised_margin_ratio)                      AS worst,
  max(realised_margin_ratio)                      AS best,
  max(price_book_margin_ratio)                    AS price_book_margin,
  round(avg(margin_gap), 4)                       AS avg_gap
FROM historical_job_calibration;

\echo ''
\echo '=== Cost per m², where the price list is validated or not ==='
SELECT
  count(*)                                        AS jobs,
  count(*) FILTER (WHERE net_area_estimated)      AS net_area_estimated,
  round(avg(actual_cost_per_m2), 2)               AS avg_actual_cost_per_m2,
  round(avg(quoted_per_m2), 2)                    AS avg_quoted_per_m2
FROM historical_job_calibration;

\echo ''
\echo '=== Coefficients this data speaks to ==='
SELECT
  round(avg(implied_crew_day_cost), 2)            AS implied_crew_day_cost,
  max(price_book_crew_day_cost)                   AS price_book_crew_day_cost,
  round(avg(implied_gross_to_net_ratio), 4)       AS implied_gross_to_net,
  max(price_book_gross_to_net_ratio)              AS price_book_gross_to_net
FROM historical_job_calibration;

\echo ''
\echo '=== Quote accuracy, where the invoice was recorded ==='
SELECT
  count(*) FILTER (WHERE quote_accuracy_ratio IS NOT NULL) AS jobs_with_invoice,
  round(avg(quote_accuracy_ratio), 4)                      AS avg_invoiced_over_quoted
FROM historical_job_calibration;
