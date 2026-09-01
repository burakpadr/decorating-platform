-- The analysis columns say what they may say (decision 0020).
--
-- V1 gave surface_finding a CHECK for every enumerated column and gave room_analysis none, so the
-- four columns describing the room as a whole were varchar(16) and nothing more. That is how
-- ceiling_staining came to be documented as NONE|LIGHT|HEAVY in the response schema while
-- CeilingFinding.isRisk() — the predicate ADR 0017 wrote so an active leak goes to a survey rather
-- than to a price — tested for an ACTIVE the model had no way to say. The rule was written and not
-- enforced, which is BOYA-22a's lesson in a second place.
--
-- The ceiling answers the same physical question as a wall — dry, once wet, wet now — so it answers
-- in the same three words as surface_finding.moisture, and the filler band is the wall's band table.
-- No data migration: nothing has ever written this table. The vision adapter (BOYA-47) is the first
-- thing that will, and BOYA-49 is what makes the rows.

ALTER TABLE room_analysis
  ADD CONSTRAINT room_analysis_furnishing_check
    CHECK (furnishing IN ('EMPTY', 'PARTIAL', 'FURNISHED')),
  ADD CONSTRAINT room_analysis_ceiling_staining_check
    CHECK (ceiling_staining IN ('NONE', 'STAIN', 'ACTIVE')),
  ADD CONSTRAINT room_analysis_ceiling_filler_check
    CHECK (ceiling_filler IN ('NONE', 'LOW', 'MEDIUM', 'HIGH', 'FULL'));

COMMENT ON COLUMN room_analysis.ceiling_staining IS
  'NONE | STAIN | ACTIVE — the same vocabulary as surface_finding.moisture. ACTIVE is a §5.9 risk finding (ADR 0017).';
