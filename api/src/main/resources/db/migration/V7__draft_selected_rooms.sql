-- The one stage 1 answer with nowhere to go.
--
-- §1.1 of the workflow asks four things in its first screen: district, area, layout, and "nerelerin
-- boyanacağı" — which of the home is being painted. quote_request has a `scope` column for
-- WHOLE_HOME | SELECTED_ROOMS and nothing to hold the selection itself, so a customer who chooses
-- SELECTED_ROOMS answers a question the schema throws away, and the estimate (BOYA-29) has nothing to
-- price. WHOLE_HOME hid it: the room list is derived from the layout, so the answer was never needed.
--
-- jsonb, an array of RoomType names, matching price_modifier.scope_items — the shape already in the
-- schema for "a list of enum values belonging to one row". Not a child table: the selection has no
-- identity, no lifecycle and nothing references it, and `room` rows are a different thing entirely
-- (they arrive at stage 2 with photos attached, from the list this answer helps derive).
--
-- Null means the question has not been reached, which is not the same as an empty selection. Stage 1's
-- answers are all boxed for that reason (StageOneAnswers), and the column follows.

ALTER TABLE quote_request ADD COLUMN selected_rooms jsonb;

COMMENT ON COLUMN quote_request.selected_rooms IS
  'RoomType names the customer chose when scope = SELECTED_ROOMS. Null until the question is answered; '
  'ignored when scope = WHOLE_HOME, where the layout derives the list.';
