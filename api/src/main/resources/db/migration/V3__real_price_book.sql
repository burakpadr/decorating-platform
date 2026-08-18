-- BOYA-1 (Phase 0, spec §15 · workflow §12): the business's actual item costs.
--
-- This migration ends Phase 0 for line items. Until now the active price book was SEED-2026-01,
-- whose figures V2 declares to be market-derived placeholders. REAL-2026-01 supersedes it with the
-- costs the business works to.
--
-- Why a new version instead of editing V2, which is what the ticket's acceptance text asks for:
--
--   * V2 has already been applied to running databases, so Flyway has its checksum on record.
--     Editing the file — comments included — fails the next startup with a checksum mismatch.
--   * Spec §4.5 and V2's own header: changing a coefficient must not retroactively alter existing
--     quotes. A quote records the price_book_id it was computed with, so history only stays
--     readable if superseded versions survive intact.
--
-- SEED-2026-01 therefore stays in place, deactivated. It is also what the §5.10 worked example was
-- derived from, so it stays useful as a fixture.
--
-- STILL PLACEHOLDER, each pending its own work item — this migration carries them forward unchanged
-- rather than inventing values:
--
--   * labour_vat_rate, material_vat_rate  → BOYA-3, accountant
--   * crew_day_cost, margin_ratio, survey_amount_factor
--   * district_factor per district
--
-- Later revisions are made through the operator UI (workflow §6, "toplu zam"), not by migration.

UPDATE price_book SET active = false WHERE active = true;

INSERT INTO price_book (
  id, version_code, active,
  ceiling_height_m, gross_to_net_ratio, stage1_opening_ratio, door_opening_m2, window_opening_m2,
  crew_size, crew_hours_per_day, crew_day_cost, day_rounding_tolerance,
  margin_ratio, margin_alert_threshold, survey_amount_factor,
  labour_vat_rate, material_vat_rate, base_band_ratio
)
SELECT
  '01930000-0000-7000-8000-000000000002', 'REAL-2026-01', true,
  ceiling_height_m, gross_to_net_ratio, stage1_opening_ratio, door_opening_m2, window_opening_m2,
  crew_size, crew_hours_per_day, crew_day_cost, day_rounding_tolerance,
  margin_ratio, margin_alert_threshold, survey_amount_factor,
  labour_vat_rate, material_vat_rate, base_band_ratio
FROM price_book
WHERE version_code = 'SEED-2026-01';

-- ---------------------------------------------------------------------------
-- Item costs — the deliverable of BOYA-1.
--
-- Costs, not sale prices: margin is applied at pricing step 12, so a figure that already contains
-- margin gets marked up twice. labour_minutes are PERSON-minutes — §5.8 divides the total by 60 for
-- person-hours and only then by crew size.
--
-- Written out in full rather than copied from the seed: this list is the business's answer, and a
-- future reader has to be able to see it without reconstructing it from a superseded version.
-- ---------------------------------------------------------------------------

INSERT INTO price_book_item (id, price_book_id, code, unit, labour_cost, material_cost, labour_minutes)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000002', code, unit, labour, material, minutes
FROM (VALUES
  ('WALL_PAINT',          'SQM',       62.00,   38.00,  6.00),
  ('CEILING_PAINT',       'SQM',       70.00,   38.00,  8.00),
  ('PATCH_FILLING',       'SQM',       50.00,   15.00, 12.00),
  ('SKIM_COAT',           'SQM',      100.00,   42.00, 22.00),
  ('PRIMER',              'SQM',       20.00,   15.00,  3.00),
  ('STAIN_BLOCK_PRIMER',  'SQM',       25.00,   40.00,  4.00),
  ('WALLPAPER_STRIPPING', 'SQM',       48.00,    2.00, 14.00),
  ('DOOR_PAINT',          'UNIT',     350.00,  150.00, 55.00),
  ('TRIM_PAINT',          'UNIT',     140.00,   52.00, 22.00),
  ('RADIATOR_PAINT',      'UNIT',     270.00,  115.00, 40.00),
  ('DOWNLIGHT_CUTTING',   'UNIT',      46.00,    0.00,  8.00),
  ('CORNICE_CUTTING',     'ROOM',     308.00,    0.00, 45.00),
  ('MASKING',             'ROOM',     115.00,   62.00, 25.00),
  ('MOBILIZATION',        'LUMP_SUM', 1900.00,   0.00, 60.00)
) AS t(code, unit, labour, material, minutes);

-- ---------------------------------------------------------------------------
-- Everything below is outside BOYA-1's scope and is carried over unchanged. A price book version is
-- self-contained — modifiers, room types and districts all hang off price_book_id — so a new
-- version that omitted them would price nothing at all.
-- ---------------------------------------------------------------------------

INSERT INTO price_modifier (id, price_book_id, code, factor, applies_to, scope_items)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000002',
       code, factor, applies_to, scope_items
FROM price_modifier
WHERE price_book_id = '01930000-0000-7000-8000-000000000001';

INSERT INTO room_type_config (
  id, price_book_id, room_type, area_weight, perimeter_factor, paintable_ratio, required_photos
)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000002',
       room_type, area_weight, perimeter_factor, paintable_ratio, required_photos
FROM room_type_config
WHERE price_book_id = '01930000-0000-7000-8000-000000000001';

INSERT INTO service_district (
  id, price_book_id, district_code, display_name, active, district_factor
)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000002',
       district_code, display_name, active, district_factor
FROM service_district
WHERE price_book_id = '01930000-0000-7000-8000-000000000001';
