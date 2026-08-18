-- Staging → historical_job / historical_job_item. Run inside one transaction; import.sql opens it,
-- HistoricalJobImportTest mirrors it. No psql meta-commands here on purpose: everything that decides
-- what enters the calibration dataset stays under the build.
--
-- Jobs are recorded as they finish (ADR 0012), so this runs repeatedly and the file will routinely
-- contain jobs already imported. Two silent failures are possible on such a run and neither is
-- allowed:
--
--   * a re-typed row overwriting a job already in the dataset — the recorded row wins, and the
--     skipped references are reported rather than swallowed. Correcting a recorded job is a
--     deliberate UPDATE, not a re-import.
--   * a work row whose job_ref matches nothing being dropped by a join — it rejects the batch instead.

CREATE TABLE import_skipped AS
SELECT DISTINCT s.job_ref
FROM staging_job s
WHERE EXISTS (SELECT 1 FROM historical_job h WHERE h.job_ref = s.job_ref);

-- v4 uuids rather than the UUIDv7 the schema conventions ask for, as in V3's item rows: nothing
-- time-orders these, and there is no application path to generate them — the import predates the UI.
INSERT INTO historical_job (
  id, job_ref, completed_on, district_code, layout, scope, furnishing, wall_condition,
  gross_area_m2, net_area_m2, door_count, quoted_total_ex_vat, actual_total_ex_vat, actual_cost,
  actual_labour_cost, actual_material_cost, actual_days, crew_size, notes)
SELECT
  gen_random_uuid(), job_ref, completed_on, district_code, layout, scope, furnishing, wall_condition,
  gross_area_m2, net_area_m2, door_count, quoted_total_ex_vat, actual_total_ex_vat, actual_cost,
  actual_labour_cost, actual_material_cost, actual_days, crew_size, notes
FROM staging_job
ON CONFLICT (job_ref) DO NOTHING;

-- The scalar subquery yields NULL where no job matches, and NOT NULL then rejects the transaction. A
-- JOIN would drop the row silently and the per-code comparison would be missing work nobody knows is
-- missing.
--
-- ON CONFLICT here is per (job, code), so a code missed on an earlier import still lands against a
-- job that is itself skipped; only a code already recorded is left alone.
INSERT INTO historical_job_item (id, historical_job_id, code, quantity)
SELECT
  gen_random_uuid(),
  (SELECT h.id FROM historical_job h WHERE h.job_ref = s.job_ref),
  s.code,
  s.quantity
FROM staging_item s
ON CONFLICT (historical_job_id, code) DO NOTHING;
