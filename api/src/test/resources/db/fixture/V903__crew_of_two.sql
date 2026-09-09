-- REAL-2026-03: the crew is two people, not three.
--
-- §5.11's illustrative coefficients said three and V3 carried the figure forward unexamined, the same
-- way it carried crew_day_cost. The business says two go to a job.
--
-- WHAT THIS DOES NOT CHANGE, which is the interesting part. Labour is derived from
-- crew_day_cost / (crew_size × crew_hours_per_day × 60) (ADR 0016), so what an item costs depends on
-- the price of a person-minute and not on how many people are in the van:
--
--   3 people at 7,500/day  →  7500 / (3 × 8 × 60)  =  5.2083 TL a person-minute
--   2 people at 5,000/day  →  5000 / (2 × 8 × 60)  =  5.2083 TL a person-minute
--
-- Both are 2,500 TL a person-day, so every one of the fourteen labour figures comes out identical and
-- no quote's cost moves. PriceBookIntegrityTest#theActiveBookKeepsItsPersonDayCost is the guard that
-- keeps the pair moving together: dropping crew_size alone would have rewritten all fourteen.
--
-- WHAT IT DOES CHANGE is duration, and duration alone:
--
--   * Days. The same work spread over two people instead of three takes half again as long. The 3+1 of
--     92 m² that reported 3 billable days now reports 5. The figure was wrong before, not now.
--   * The floor. §5.8's minimum is billableDays × crew_day_cost, so a one-day job now floors at 5,000
--     rather than 7,500. That is a real price change on small jobs, and the right direction: two people
--     turned up, not three.
--
-- THE MINUTES ARE LEFT ALONE, deliberately. Asked how long an 80 m² flat takes, the business said two
-- people for three days including the doors — 48 person-hours. This book says 41.6 for an empty one and
-- 51.7 for a furnished one, so it is 13% low or 8% high depending on which was meant. The gap between
-- the two readings is wider than the error, and calibrating on a recollection would be fitting noise
-- into the one figure every labour price now depends on. Real durations arrive from the job record
-- (BOYA-2a, ADR 0012), which is the mechanism that was built for exactly this question.

UPDATE price_book SET active = false WHERE active = true;

INSERT INTO price_book (
  id, version_code, active,
  ceiling_height_m, gross_to_net_ratio, stage1_opening_ratio, door_opening_m2, window_opening_m2,
  crew_size, crew_hours_per_day, crew_day_cost, day_rounding_tolerance,
  margin_ratio, margin_alert_threshold, survey_amount_factor,
  labour_vat_rate, material_vat_rate, base_band_ratio
)
SELECT
  '01930000-0000-7000-8000-000000000004', 'REAL-2026-03', true,
  ceiling_height_m, gross_to_net_ratio, stage1_opening_ratio, door_opening_m2, window_opening_m2,
  2, crew_hours_per_day, 5000.00, day_rounding_tolerance,
  margin_ratio, margin_alert_threshold, survey_amount_factor,
  labour_vat_rate, material_vat_rate, base_band_ratio
FROM price_book
WHERE version_code = 'REAL-2026-02';

-- Items: minutes and materials carried over, labour re-derived from this version's own crew rate. Same
-- expression as V5, and it has to be — a copied figure would be the second statement about labour that
-- ADR 0016 exists to remove.
INSERT INTO price_book_item (id, price_book_id, code, unit, labour_cost, material_cost, labour_minutes)
SELECT
  gen_random_uuid(),
  target.id,
  source.code,
  source.unit,
  round(source.labour_minutes * target.crew_day_cost
        / (target.crew_size * target.crew_hours_per_day * 60), 2),
  source.material_cost,
  source.labour_minutes
FROM price_book_item source
JOIN price_book previous ON previous.id = source.price_book_id
CROSS JOIN price_book target
WHERE previous.version_code = 'REAL-2026-02'
  AND target.version_code = 'REAL-2026-03';

INSERT INTO price_modifier (id, price_book_id, code, factor, applies_to, scope_items)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000004',
       code, factor, applies_to, scope_items
FROM price_modifier
WHERE price_book_id = '01930000-0000-7000-8000-000000000003';

INSERT INTO room_type_config (
  id, price_book_id, room_type, area_weight, perimeter_factor, paintable_ratio, required_photos
)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000004',
       room_type, area_weight, perimeter_factor, paintable_ratio, required_photos
FROM room_type_config
WHERE price_book_id = '01930000-0000-7000-8000-000000000003';

INSERT INTO service_district (
  id, price_book_id, district_code, display_name, active, district_factor
)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000004',
       district_code, display_name, active, district_factor
FROM service_district
WHERE price_book_id = '01930000-0000-7000-8000-000000000003';
