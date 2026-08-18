-- BOYA-2: load the record of the last 50 jobs, then report on it.
--
-- Run once, against a database Flyway has already migrated to V4. The two filled-in files must sit
-- in the directory psql is started from, under exactly these names:
--
--   historical-jobs.csv
--   historical-job-items.csv
--
--   cd <the directory holding the two filled-in files>
--   psql "$DATABASE_URL" -f <path to>/import.sql
--
-- Fixed names rather than psql variables because \copy does not interpolate them, and the
-- alternative — a server-side COPY — would need the files readable by the Postgres process itself.
-- If the work items have not been collected yet, put the template's header line in an otherwise
-- empty historical-job-items.csv; the import then loads the job rows alone.
--
-- One transaction: either every row lands or none does, so a rejected row never leaves a
-- half-imported set behind that a second run would then double.

\set ON_ERROR_STOP on
\timing off

BEGIN;

CREATE TEMP TABLE staging_job (
  job_ref               varchar(32),
  completed_on          date,
  district_code         varchar(32),
  layout                varchar(16),
  scope                 varchar(16),
  furnishing            varchar(16),
  wall_condition        varchar(16),
  gross_area_m2         numeric(7,2),
  net_area_m2           numeric(7,2),
  door_count            integer,
  quoted_total_ex_vat   numeric(14,2),
  actual_total_ex_vat   numeric(14,2),
  actual_cost           numeric(14,2),
  actual_labour_cost    numeric(14,2),
  actual_material_cost  numeric(14,2),
  actual_days           integer,
  crew_size             integer,
  notes                 text
) ON COMMIT DROP;

CREATE TEMP TABLE staging_item (
  job_ref   varchar(32),
  code      varchar(32),
  quantity  numeric(10,2)
) ON COMMIT DROP;

\copy staging_job FROM 'historical-jobs.csv' WITH (FORMAT csv, HEADER true)
\copy staging_item FROM 'historical-job-items.csv' WITH (FORMAT csv, HEADER true)

-- The ids are generated here rather than by the application because there is no application path
-- for this import: it happens once, before the software has a UI. v4 rather than the UUIDv7 the
-- schema conventions ask for, as in V3's item rows — nothing time-orders these.
INSERT INTO historical_job (
  id, job_ref, completed_on, district_code, layout, scope, furnishing, wall_condition,
  gross_area_m2, net_area_m2, door_count, quoted_total_ex_vat, actual_total_ex_vat, actual_cost,
  actual_labour_cost, actual_material_cost, actual_days, crew_size, notes)
SELECT
  gen_random_uuid(), job_ref, completed_on, district_code, layout, scope, furnishing, wall_condition,
  gross_area_m2, net_area_m2, door_count, quoted_total_ex_vat, actual_total_ex_vat, actual_cost,
  actual_labour_cost, actual_material_cost, actual_days, crew_size, notes
FROM staging_job;

-- A work row whose job_ref matches nothing must fail the import, not vanish. A JOIN would drop it
-- silently and the per-code comparison would then be missing work nobody knows is missing; the
-- scalar subquery yields NULL instead and NOT NULL rejects the transaction.
INSERT INTO historical_job_item (id, historical_job_id, code, quantity)
SELECT
  gen_random_uuid(),
  (SELECT h.id FROM historical_job h WHERE h.job_ref = s.job_ref),
  s.code,
  s.quantity
FROM staging_item s;

COMMIT;

\echo ''
\echo '=== Imported ==='
SELECT count(*) AS jobs, min(completed_on) AS earliest, max(completed_on) AS latest
FROM historical_job;

\echo ''
\echo '=== Work codes the active price book has no row for (read this first) ==='
SELECT * FROM historical_job_unknown_item_code ORDER BY code, job_ref;

\echo ''
\echo '=== Realised margin against the active price book ==='
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
