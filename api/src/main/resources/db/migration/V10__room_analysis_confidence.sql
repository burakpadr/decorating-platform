-- Four columns §4.4's list is missing, found by writing the first row into it (BOYA-49, decision 0021).
--
-- ceiling_confidence — §4.4 gave room_analysis a column for what the ceiling looks like and none for
-- how well it was seen, which was consistent while §5.6 read neither ceiling column at all. ADR 0017
-- changed that: a stained ceiling now takes stain-block primer over its whole plane, so how well the
-- ceiling was read is part of how well the room was read, and §6's room confidence includes it.
--
-- reported_confidence — the model's own figure for the room, which the response has always carried and
-- nothing normalised. It is not `confidence`: that holds §6's average over the planes, which is what
-- the evaluator and the band act on. This one is evidence — the scalar a prompt-version comparison
-- wants, and §4.4 keeps raw_response for exactly that comparison.
--
-- unusable_photos and notes — required by the response schema, and both have readers. §6's first
-- decision branch is "unusablePhotos not empty && recaptureCount == 0 → RECAPTURE" (BOYA-51), and the
-- labels are what makes the request specific: not "retake your photos" but "the second wall of the
-- living room came out dark" (BOYA-61). notes are the Turkish sentences §6 insists on because the
-- operator reads them on the review screen (BOYA-53).
--
-- Leaving those two in raw_response would have made the audit trail load-bearing: every reader would
-- parse JSON to reach them, which is the arrangement §4.4 built surface_finding to avoid. A value the
-- system acts on is a column. The same rule decision 0020 applied to a vocabulary.
--
-- text[] rather than jsonb: notes are free Turkish prose. The hand-rolled parsing used for
-- selected_rooms and required_photos is safe only because those hold enum names with nothing to
-- escape.
--
-- NOT NULL where the neighbouring columns are nullable, and no defaults: the response schema requires
-- all four, RoomAnalysis refuses to exist without them, and the table is empty in every environment
-- because BOYA-47 is the first thing that can write it.

ALTER TABLE room_analysis
  ADD COLUMN ceiling_confidence  numeric(4,3) NOT NULL,
  ADD COLUMN reported_confidence numeric(4,3) NOT NULL,
  ADD COLUMN unusable_photos     text[] NOT NULL,
  ADD COLUMN notes               text[] NOT NULL;

COMMENT ON COLUMN room_analysis.confidence IS
  'The room confidence §6 defines: the average over every plane read, ceiling included (decision 0021). What §5.9 widens the band from and what §6 thresholds against — not the model''s own figure.';
COMMENT ON COLUMN room_analysis.reported_confidence IS
  'What the model said about itself. Evidence for comparing prompt versions, never the number acted on.';
COMMENT ON COLUMN room_analysis.unusable_photos IS
  'Frame labels the model could not use. §6 turns a non-empty list into one RECAPTURE request, and the labels are what makes that request specific.';
COMMENT ON COLUMN room_analysis.notes IS
  'Turkish. The operator reads these on the review screen (§6).';

-- room_analysis has no room_type column and does not need one: the model reports what it thinks the
-- room is, room.room_type is what the customer said, and §5.3 prices the second. The model's reading
-- stays in raw_response as evidence rather than becoming a second answer to "which room is this".
COMMENT ON TABLE room_analysis IS
  'One analysis per room (room_id UNIQUE): a re-analysis after a recapture replaces its predecessor.';
