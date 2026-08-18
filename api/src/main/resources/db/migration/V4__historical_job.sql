-- BOYA-2 (Phase 0, workflow §12): the record of the last 50 jobs.
--
-- Ev tipi, m², yapılan işler, verilen fiyat, gerçekleşen maliyet — the second half of Phase 0. Its
-- purpose is stated in the workflow document: it validates the price list and makes the first
-- calibration of the coefficients possible. Both need the data to exist; this migration is where it
-- lands and the arithmetic that reads it.
--
-- WHY NOT job_outcome. That table records stage 8 against a quote the system produced, so its
-- quote_request_id is NOT NULL and a foreign key. Jobs finished before the system existed have no
-- quote request and never will. Relaxing that column instead would have been worse: job_outcome is
-- the calibration input Phase 2 compares estimates against (spec §15), and a nullable link would
-- mix rows the engine priced with rows it never saw — which is precisely the distinction Phase 2
-- turns on.
--
-- NO PERSONAL DATA. These rows are kept indefinitely: they are the baseline every later calibration
-- is measured against, so they outlive every retention window in §12. That is only defensible while
-- the record is about a job and not about a person — hence a district, and no name, phone or address.
-- HistoricalJobSchemaTest asserts it, so adding such a column fails the build.

CREATE TABLE historical_job (
  id                    uuid PRIMARY KEY,
  job_ref               varchar(32) NOT NULL UNIQUE,   -- the business's own reference; the import key
  completed_on          date NOT NULL,

  -- What the job was. Nullable throughout: these are records kept for other purposes, and a job
  -- whose furnishing nobody wrote down is still evidence about cost per m².
  district_code         varchar(32),
  layout                varchar(16),
  scope                 varchar(16),
  furnishing            varchar(16),
  wall_condition        varchar(16),
  gross_area_m2         numeric(7,2),
  net_area_m2           numeric(7,2),
  door_count            integer,

  -- Money, all excluding VAT. The two VAT rates are still placeholders (BOYA-3), so a VAT-inclusive
  -- figure could not be converted here without inventing the rate it was charged at. The business
  -- knows what it invoiced; it does the subtraction once, at intake.
  quoted_total_ex_vat   numeric(14,2) NOT NULL,        -- verilen fiyat
  actual_total_ex_vat   numeric(14,2),                 -- kesilen fatura, if it differed from the quote
  actual_cost           numeric(14,2) NOT NULL,        -- gerçekleşen maliyet
  actual_labour_cost    numeric(14,2),
  actual_material_cost  numeric(14,2),

  actual_days           integer,
  crew_size             integer,

  notes                 text,
  recorded_at           timestamptz NOT NULL DEFAULT now(),

  -- Every calibration figure is per m². A row with neither area is not a weak data point, it is an
  -- unusable one, and it would quietly drag any average it entered.
  CONSTRAINT historical_job_area_recorded_check CHECK (gross_area_m2 IS NOT NULL OR net_area_m2 IS NOT NULL),
  CONSTRAINT historical_job_gross_area_check  CHECK (gross_area_m2 IS NULL OR gross_area_m2 > 0),
  CONSTRAINT historical_job_net_area_check    CHECK (net_area_m2 IS NULL OR net_area_m2 > 0),
  CONSTRAINT historical_job_net_not_above_gross_check CHECK (
    gross_area_m2 IS NULL OR net_area_m2 IS NULL OR net_area_m2 <= gross_area_m2),
  CONSTRAINT historical_job_quoted_check      CHECK (quoted_total_ex_vat > 0),
  CONSTRAINT historical_job_invoiced_check    CHECK (actual_total_ex_vat IS NULL OR actual_total_ex_vat > 0),
  CONSTRAINT historical_job_cost_check        CHECK (actual_cost > 0),
  CONSTRAINT historical_job_days_check        CHECK (actual_days IS NULL OR actual_days > 0),
  CONSTRAINT historical_job_crew_size_check   CHECK (crew_size IS NULL OR crew_size > 0),

  -- A split that does not reconcile is a transcription error, and it matters: the FURNISHED
  -- surcharge applies to labour only (§5.7), so labour and material calibrate separately. One lira
  -- of tolerance for rounding in the source ledger.
  CONSTRAINT historical_job_cost_split_check CHECK (
    actual_labour_cost IS NULL OR actual_material_cost IS NULL
    OR abs(actual_labour_cost + actual_material_cost - actual_cost) <= 1.00),

  CONSTRAINT historical_job_scope_check CHECK (scope IN ('WHOLE_HOME', 'SELECTED_ROOMS')),
  CONSTRAINT historical_job_furnishing_check CHECK (furnishing IN ('EMPTY', 'PARTIAL', 'FURNISHED')),
  CONSTRAINT historical_job_wall_condition_check CHECK (
    wall_condition IN ('GOOD', 'MINOR', 'MAJOR', 'UNSURE'))
);

COMMENT ON TABLE historical_job IS
  'BOYA-2: jobs completed before the system existed. Phase 0 calibration baseline, not stage 8 — '
  'see job_outcome for that. Carries no personal data because it is never deleted.';

COMMENT ON COLUMN historical_job.job_ref IS
  'The business''s own reference for the job. Unique so re-running an import cannot double-count it.';

-- Deliberately not a foreign key into service_district, for two reasons: districts are per price
-- book version, and a past job may be somewhere the business no longer serves. Recording it as text
-- keeps the evidence; the calibration report joins on it where it matches.
COMMENT ON COLUMN historical_job.district_code IS
  'District as recorded. Intentionally not a foreign key: past jobs predate today''s service area.';

COMMENT ON COLUMN historical_job.layout IS
  'STUDIO | ONE_PLUS_ONE | TWO_PLUS_ONE | … as quote_request.layout. Unconstrained there, so '
  'unconstrained here: the list is still open.';

CREATE INDEX historical_job_completed_idx ON historical_job (completed_on);
CREATE INDEX historical_job_district_idx ON historical_job (district_code)
  WHERE district_code IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Yapılan işler — one row per work item per job, in the vocabulary the engine prices in.
--
-- No unit column. The unit belongs to the code and already lives in price_book_item; a second copy
-- could only ever contradict the first, and a quantity recorded in the wrong unit is exactly the
-- kind of error that survives review and comes out as a wrong average.
-- ---------------------------------------------------------------------------

CREATE TABLE historical_job_item (
  id                 uuid PRIMARY KEY,
  historical_job_id  uuid NOT NULL REFERENCES historical_job(id) ON DELETE CASCADE,
  code               varchar(32) NOT NULL,
  quantity           numeric(10,2) NOT NULL,
  CONSTRAINT historical_job_item_job_code_key UNIQUE (historical_job_id, code),
  CONSTRAINT historical_job_item_quantity_check CHECK (quantity > 0)
);

COMMENT ON COLUMN historical_job_item.code IS
  'A price_book_item code. Not a foreign key — codes are per price book version, and a job may '
  'record work the current book has no row for. historical_job_unknown_item_code reports those.';

-- ---------------------------------------------------------------------------
-- The comparison itself: one row per historical job, against the active price book.
--
-- A view rather than a report in application code. There is no application code yet — increment 1
-- starts at PricingEngine (§17) — and the two things BOYA-2 asks for, validating the price list and
-- a first pass at the coefficients, are queries over evidence, not a feature.
--
-- What this view cannot do: reprice a historical job through the engine. That needs the engine, its
-- room list and per-room measurements this record does not carry. It compares totals, per-m² figures
-- and the coefficients that are derivable from totals. The full comparison is Phase 2 (spec §15),
-- against job_outcome rows the engine actually priced.
-- ---------------------------------------------------------------------------

CREATE VIEW historical_job_calibration AS
SELECT
  h.job_ref,
  h.completed_on,
  h.district_code,
  h.layout,
  h.furnishing,

  -- Net area is what everything is divided by. Where only gross was recorded it is derived with the
  -- active book's ratio — a coefficient that is itself uncalibrated, so the flag travels with it.
  round(COALESCE(h.net_area_m2, h.gross_area_m2 * b.gross_to_net_ratio), 2) AS net_area_m2_used,
  h.net_area_m2 IS NULL AS net_area_estimated,

  h.quoted_total_ex_vat,
  COALESCE(h.actual_total_ex_vat, h.quoted_total_ex_vat) AS invoiced_total_ex_vat,
  h.actual_cost,
  h.actual_days,

  round(h.quoted_total_ex_vat
        / COALESCE(h.net_area_m2, h.gross_area_m2 * b.gross_to_net_ratio), 2) AS quoted_per_m2,
  round(h.actual_cost
        / COALESCE(h.net_area_m2, h.gross_area_m2 * b.gross_to_net_ratio), 2) AS actual_cost_per_m2,

  -- Quote accuracy is only knowable where the invoice was recorded. NULL means unrecorded; reading
  -- it as "invoiced exactly what was quoted" would flatter the estimate.
  round(h.actual_total_ex_vat / h.quoted_total_ex_vat, 4) AS quote_accuracy_ratio,

  -- Margin against what was actually charged, not what was offered. A job that ran over and was
  -- invoiced higher earned more than its quote implies, and the price list has to be measured
  -- against the money that arrived.
  round((COALESCE(h.actual_total_ex_vat, h.quoted_total_ex_vat) - h.actual_cost) / h.actual_cost, 4)
    AS realised_margin_ratio,
  b.margin_ratio AS price_book_margin_ratio,
  round((COALESCE(h.actual_total_ex_vat, h.quoted_total_ex_vat) - h.actual_cost) / h.actual_cost, 4)
    - b.margin_ratio AS margin_gap,

  -- Coefficients this record can speak to directly.
  round(h.actual_labour_cost / h.actual_days, 2) AS implied_crew_day_cost,
  b.crew_day_cost AS price_book_crew_day_cost,
  round(h.net_area_m2 / h.gross_area_m2, 4) AS implied_gross_to_net_ratio,
  b.gross_to_net_ratio AS price_book_gross_to_net_ratio,

  b.version_code AS price_book_version
FROM historical_job h
CROSS JOIN (
  SELECT version_code, margin_ratio, crew_day_cost, gross_to_net_ratio
  FROM price_book WHERE active = true
) b;

COMMENT ON VIEW historical_job_calibration IS
  'BOYA-2: each recorded job against the active price book. Empty until the records are imported.';

-- A work code with no row in the active book compares the job against nothing, and drops out of any
-- per-code comparison without appearing to. Reported rather than rejected: the business's ledger is
-- allowed to contain work this system does not price, and the import must not be the place that
-- decides otherwise.
CREATE VIEW historical_job_unknown_item_code AS
SELECT h.job_ref, i.code, i.quantity
FROM historical_job_item i
JOIN historical_job h ON h.id = i.historical_job_id
WHERE NOT EXISTS (
  SELECT 1
  FROM price_book_item p
  JOIN price_book b ON b.id = p.price_book_id
  WHERE b.active = true AND p.code = i.code
);

COMMENT ON VIEW historical_job_unknown_item_code IS
  'Work codes in the imported records that the active price book has no row for. Must be read '
  'before any per-code comparison; silently, they compare against nothing.';
