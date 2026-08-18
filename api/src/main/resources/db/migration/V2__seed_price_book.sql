-- Default price book seed. See docs/engineering/implementation-spec.md §5.3, §5.7, §5.11.
--
-- PLACEHOLDER DATA. These are market-derived figures, not this business's costs. Spec §15 Phase 0
-- says the real numbers must be extracted from the last 50 jobs before launch, and §16 lists the
-- items that need external input:
--
--   * every labour_cost / material_cost / labour_minutes value below  → business
--   * crew_day_cost, margin_ratio, survey_amount_factor               → business
--   * district_factor per district                                    → business
--   * labour_vat_rate, material_vat_rate                              → ACCOUNTANT
--     (legislation treats labour and materials differently for residential painting)
--
-- Replace by creating a new price_book version through the operator UI, never by editing this
-- migration — changing a coefficient must not retroactively alter existing quotes.

INSERT INTO price_book (
  id, version_code, active, crew_day_cost, labour_vat_rate, material_vat_rate
) VALUES (
  '01930000-0000-7000-8000-000000000001',
  'SEED-2026-01',
  true,
  4500.00,
  0.2000,   -- placeholder, pending accountant
  0.2000    -- placeholder, pending accountant
);

-- ---------------------------------------------------------------------------
-- Items — costs, not sale prices. Margin is applied at pricing step 12.
-- ---------------------------------------------------------------------------

INSERT INTO price_book_item (id, price_book_id, code, unit, labour_cost, material_cost, labour_minutes)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000001', code, unit, labour, material, minutes
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
-- Modifiers. Order matters — see pricing step order in §5.2.
--
-- The labour/material split exists for these: a furnished home consumes the same paint and more
-- time, so applying the furnishing surcharge to materials systematically overprices furnished
-- jobs. DARK_TO_LIGHT means a third coat — more of both.
-- ---------------------------------------------------------------------------

INSERT INTO price_modifier (id, price_book_id, code, factor, applies_to, scope_items)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000001', code, factor, applies_to, scope
FROM (VALUES
  ('FURNISHED',     1.2500, 'LABOUR', NULL::jsonb),
  ('RUSH',          1.2500, 'LABOUR', NULL::jsonb),
  ('DARK_TO_LIGHT', 1.5000, 'BOTH',   '["WALL_PAINT","DOOR_PAINT"]'::jsonb),
  ('NO_ELEVATOR',   1.2000, 'BOTH',   '["MOBILIZATION"]'::jsonb)
) AS t(code, factor, applies_to, scope);

-- ---------------------------------------------------------------------------
-- Room types. perimeter_factor varies by type because the square-room assumption (4.0) badly
-- underestimates elongated spaces — a 1.2 × 6.5 m hallway sits ~40% above it.
-- ---------------------------------------------------------------------------

INSERT INTO room_type_config (
  id, price_book_id, room_type, area_weight, perimeter_factor, paintable_ratio, required_photos
)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000001', room_type, weight, perimeter, paintable, photos
FROM (VALUES
  ('LIVING_ROOM',    3.00, 4.10, 1.0000, '["WALL_1","WALL_2","WALL_3","WALL_4","CEILING"]'::jsonb),
  ('MASTER_BEDROOM', 1.50, 4.10, 1.0000, '["WALL_1","WALL_2","WALL_3","WALL_4","CEILING"]'::jsonb),
  ('BEDROOM',        1.20, 4.10, 1.0000, '["WALL_1","WALL_2","WALL_3","WALL_4","CEILING"]'::jsonb),
  ('STUDY',          1.00, 4.10, 1.0000, '["WALL_1","WALL_2","WALL_3","WALL_4","CEILING"]'::jsonb),
  ('KITCHEN',        1.10, 4.30, 0.6500, '["WALL_1","WALL_2","CEILING"]'::jsonb),
  ('BATHROOM',       0.50, 4.20, 0.2000, '["WALL_1","CEILING"]'::jsonb),
  ('HALLWAY',        0.80, 5.50, 1.0000, '["WALL_1","WALL_2","CEILING"]'::jsonb),
  ('BALCONY',        0.40, 4.30, 1.0000, '["WALL_1","CEILING"]'::jsonb)
) AS t(room_type, weight, perimeter, paintable, photos);

-- ---------------------------------------------------------------------------
-- Service districts: all 39 Istanbul districts, active, factor 1.0000.
-- Codes are ASCII English-rule identifiers; display_name is the Turkish name shown to customers
-- and used in the /{district}-boya-badana-fiyatlari SEO routes.
-- ---------------------------------------------------------------------------

INSERT INTO service_district (id, price_book_id, district_code, display_name)
SELECT gen_random_uuid(), '01930000-0000-7000-8000-000000000001', code, name
FROM (VALUES
  ('ADALAR',         'Adalar'),
  ('ARNAVUTKOY',     'Arnavutköy'),
  ('ATASEHIR',       'Ataşehir'),
  ('AVCILAR',        'Avcılar'),
  ('BAGCILAR',       'Bağcılar'),
  ('BAHCELIEVLER',   'Bahçelievler'),
  ('BAKIRKOY',       'Bakırköy'),
  ('BASAKSEHIR',     'Başakşehir'),
  ('BAYRAMPASA',     'Bayrampaşa'),
  ('BESIKTAS',       'Beşiktaş'),
  ('BEYKOZ',         'Beykoz'),
  ('BEYLIKDUZU',     'Beylikdüzü'),
  ('BEYOGLU',        'Beyoğlu'),
  ('BUYUKCEKMECE',   'Büyükçekmece'),
  ('CATALCA',        'Çatalca'),
  ('CEKMEKOY',       'Çekmeköy'),
  ('ESENLER',        'Esenler'),
  ('ESENYURT',       'Esenyurt'),
  ('EYUPSULTAN',     'Eyüpsultan'),
  ('FATIH',          'Fatih'),
  ('GAZIOSMANPASA',  'Gaziosmanpaşa'),
  ('GUNGOREN',       'Güngören'),
  ('KADIKOY',        'Kadıköy'),
  ('KAGITHANE',      'Kağıthane'),
  ('KARTAL',         'Kartal'),
  ('KUCUKCEKMECE',   'Küçükçekmece'),
  ('MALTEPE',        'Maltepe'),
  ('PENDIK',         'Pendik'),
  ('SANCAKTEPE',     'Sancaktepe'),
  ('SARIYER',        'Sarıyer'),
  ('SILIVRI',        'Silivri'),
  ('SULTANBEYLI',    'Sultanbeyli'),
  ('SULTANGAZI',     'Sultangazi'),
  ('SILE',           'Şile'),
  ('SISLI',          'Şişli'),
  ('TUZLA',          'Tuzla'),
  ('UMRANIYE',       'Ümraniye'),
  ('USKUDAR',        'Üsküdar'),
  ('ZEYTINBURNU',    'Zeytinburnu')
) AS t(code, name);
