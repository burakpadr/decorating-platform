-- Staging tables for the job import: the CSV files, verbatim, before any constraint applies.
--
-- Two reasons they are not TEMP tables. import.sql creates them inside its transaction, so a failed
-- import rolls the tables away with the rows; and HistoricalJobImportTest needs to load them over
-- JDBC, where a temp table would not survive between pooled connections.
--
-- Column order is the CSV header order, which is the column order of historical_job. The two names
-- missing from it are historical_job's id and recorded_at: the system owns those, so they are never
-- typed.
--
-- No DROP IF EXISTS. import.sql creates and drops these inside one transaction, so a crashed run
-- leaves nothing behind; a table that does exist means something else is going on and CREATE saying
-- so is better than dropping it. The test clears them itself.

CREATE TABLE staging_job (
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
);

CREATE TABLE staging_item (
  job_ref   varchar(32),
  code      varchar(32),
  quantity  numeric(10,2)
);
