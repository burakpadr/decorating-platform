-- REAL-2026-02: the first calibration pass (workflow §12), and a data correction.
--
-- WHAT WAS WRONG. A price book states the cost of labour twice — once as crew_day_cost, once as a
-- TL figure on every item — and REAL-2026-01's two statements disagreed by up to 3.3x. V3's header
-- already stated the rule it broke ("Costs, not sale prices: margin is applied at pricing step 12,
-- so a figure that already contains margin gets marked up twice" · "labour_minutes are PERSON-
-- minutes"). Nothing in the build compared the two, so the break was invisible until an operator
-- said the quotes were roughly twice what the business charges.
--
-- The arithmetic, on a 3+1 flat of 92 m² net (7.7 person-days of work):
--
--   crew_day_cost 4,500 over 3 people        →   1,500 TL per person-day
--   WALL_PAINT 62.00 TL/m² over 6 minutes    →   4,960 TL per person-day   (3.3x)
--   labour billed 33,321 TL · crew cost      →  13,500 TL
--
-- Every item was above the book's own rate, WALL_PAINT — 54% of a typical bill — worst of all. The
-- engine believes both figures: it bills labour from the items and floors it at crew_day_cost, so
-- the floor never bound and ~20,000 TL of a 74,010 TL quote answered to nothing.
--
-- THE FIX, and why it is not a discount. labour_cost is no longer entered. It is DERIVED from the
-- item's own minutes at the book's crew rate, by the expression below, so the two statements cannot
-- disagree again. Four items go UP (PATCH_FILLING, SKIM_COAT, WALLPAPER_STRIPPING, MASKING were
-- priced BELOW the crew rate — unbillable time). PriceBookIntegrityTest
-- #activeItemLabourCostsReconcileWithTheCrewDayCost is the guard; see docs/decisions/0016.
--
-- TWO FIGURES CAME FROM THE BUSINESS, and they are the only new inputs here:
--
--   * crew_day_cost 7,500 — a painter costs 2,500 TL per person-day (wage, insurance, food),
--     three to a crew. REAL-2026-01 carried §5.11's illustrative 4,500 unexamined.
--   * paint material 22.00 TL/m² — a 15 l tub at ~1,800 TL is ~120 TL/l, and two coats take
--     ~0.18 l/m². REAL-2026-01's 38.00 implies ~210 TL/l, a premium tin this business does not buy.
--     Provisional: the operator did not have the tub price to hand, so this is derived from coverage
--     and is the first thing to correct when the invoice is looked up.
--
-- STILL UNEXAMINED, each carried forward unchanged: material costs other than paint, margin_ratio,
-- survey_amount_factor, district_factor, the VAT rates (BOYA-3), and every coefficient in §5.3–5.5.
-- MOBILIZATION is the one item whose TL figure was not purely crew time: its 1,900 TL covers the
-- van, fuel and travel. Splitting it — 60 minutes of crew time as labour, the rest as material —
-- keeps the total at 1,900 and lets the invariant hold without an exemption.

UPDATE price_book SET active = false WHERE active = true;

INSERT INTO price_book (
  id, version_code, active,
  ceiling_height_m, gross_to_net_ratio, stage1_opening_ratio, door_opening_m2, window_opening_m2,
  crew_size, crew_hours_per_day, crew_day_cost, day_rounding_tolerance,
  margin_ratio, margin_alert_threshold, survey_amount_factor,
  labour_vat_rate, material_vat_rate, base_band_ratio
)
SELECT
  '01930000-0000-7000-8000-000000000003', 'REAL-2026-02', true,
  ceiling_height_m, gross_to_net_ratio, stage1_opening_ratio, door_opening_m2, window_opening_m2,
  crew_size, crew_hours_per_day, 7500.00, day_rounding_tolerance,
  margin_ratio, margin_alert_threshold, survey_amount_factor,
  labour_vat_rate, material_vat_rate, base_band_ratio
FROM price_book
WHERE version_code = 'REAL-2026-01';

-- ---------------------------------------------------------------------------
-- Items. Only minutes and material are stated; labour is computed from the crew rate, which is what
-- makes the invariant structural rather than transcribed. The TL figures this produces, against
-- REAL-2026-01:
--
--   WALL_PAINT          62.00 →  31.25/m²      DOOR_PAINT         350.00 → 286.46/unit
--   CEILING_PAINT       70.00 →  41.67/m²      TRIM_PAINT         140.00 → 114.58/unit
--   PATCH_FILLING       50.00 →  62.50/m² ↑    RADIATOR_PAINT     270.00 → 208.33/unit
--   SKIM_COAT          100.00 → 114.58/m² ↑    DOWNLIGHT_CUTTING   46.00 →  41.67/unit
--   PRIMER              20.00 →  15.63/m²      CORNICE_CUTTING    308.00 → 234.38/room
--   STAIN_BLOCK_PRIMER  25.00 →  20.83/m²      MASKING            115.00 → 130.21/room ↑
--   WALLPAPER_STRIPPING 48.00 →  72.92/m² ↑    MOBILIZATION      1900.00 → 312.50 + 1587.50 mat
-- ---------------------------------------------------------------------------

INSERT INTO price_book_item (id, price_book_id, code, unit, labour_cost, material_cost, labour_minutes)
SELECT
  gen_random_uuid(),
  b.id,
  t.code,
  t.unit,
  round(t.minutes * b.crew_day_cost / (b.crew_size * b.crew_hours_per_day * 60), 2),
  t.material,
  t.minutes
FROM price_book b
CROSS JOIN (VALUES
  ('WALL_PAINT',          'SQM',        22.00,  6.00),
  ('CEILING_PAINT',       'SQM',        22.00,  8.00),
  ('PATCH_FILLING',       'SQM',        15.00, 12.00),
  ('SKIM_COAT',           'SQM',        42.00, 22.00),
  ('PRIMER',              'SQM',        15.00,  3.00),
  ('STAIN_BLOCK_PRIMER',  'SQM',        40.00,  4.00),
  ('WALLPAPER_STRIPPING', 'SQM',         2.00, 14.00),
  ('DOOR_PAINT',          'UNIT',      150.00, 55.00),
  ('TRIM_PAINT',          'UNIT',       52.00, 22.00),
  ('RADIATOR_PAINT',      'UNIT',      115.00, 40.00),
  ('DOWNLIGHT_CUTTING',   'UNIT',        0.00,  8.00),
  ('CORNICE_CUTTING',     'ROOM',        0.00, 45.00),
  ('MASKING',             'ROOM',       62.00, 25.00),
  ('MOBILIZATION',        'LUMP_SUM', 1587.50, 60.00)
) AS t(code, unit, material, minutes)
WHERE b.version_code = 'REAL-2026-02';

-- ---------------------------------------------------------------------------
-- Carried over unchanged: a version prices nothing without them (see V3).
-- ---------------------------------------------------------------------------

INSERT INTO price_modifier (id, price_book_id, code, factor, applies_to, scope_items)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000003',
       code, factor, applies_to, scope_items
FROM price_modifier
WHERE price_book_id = '01930000-0000-7000-8000-000000000002';

INSERT INTO room_type_config (
  id, price_book_id, room_type, area_weight, perimeter_factor, paintable_ratio, required_photos
)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000003',
       room_type, area_weight, perimeter_factor, paintable_ratio, required_photos
FROM room_type_config
WHERE price_book_id = '01930000-0000-7000-8000-000000000002';

INSERT INTO service_district (
  id, price_book_id, district_code, display_name, active, district_factor
)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000003',
       district_code, display_name, active, district_factor
FROM service_district
WHERE price_book_id = '01930000-0000-7000-8000-000000000002';
