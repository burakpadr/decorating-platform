# 21. What a stored analysis is made of

Date: 2026-09-01
Status: accepted

## Context

BOYA-49 writes the first `room_analysis` row. Doing that settles three things §4.4 and §6 left open,
and one of them changes a price.

§6 says "room confidence is the **weighted average** of the surface confidences, not the minimum" and
names no weights. None exist. §5.4 gives a room a single gross wall area, so there is no per-wall
area to weigh by; the only weighting the data could support is equal.

§6 also says *surfaces*, and a ceiling is not a surface — `surface_finding` rows are walls. That is
the same sentence ADR 0017 caught letting an actively leaking ceiling price itself. Since 0017 the
ceiling carries cost of its own: stain block over its whole plane, filler over its own area.

And §4.4's column list turns out to be short. `room_analysis` has no column for the ceiling's
confidence, none for the model's own room-level figure, none for `unusablePhotos`, and none for
`notes` — the last two required by the response schema and both with named readers waiting.

## Decision

**Room confidence is the plain average over every plane the model read, ceiling included, rounded to
the three decimals `numeric(4,3)` holds.** "Weighted" is read as ruling out the minimum, which is what
the sentence goes on to say and what its stated reason is — one blurry frame must not poison a room.
Every plane counting once is the least-invented rule that obeys it. Inventing a weighting scheme with
no evidence behind it would be a number nobody could defend to the business, in the one figure that
widens the band and decides between AUTO and SURVEY.

Including the ceiling is a step past §6's literal text and it follows 0017: the plane is priced, so
how well it was read belongs in how well the room was read. A room photographed sharply at eye level
and barely at all overhead is not a confident room, and before this it reported as one.

The consequence to state plainly: the ceiling's share of the figure depends on the capture shape. A
four-wall bedroom gives it a fifth; a corner-shot kitchen gives it a half, because that room genuinely
produced two readings rather than five. Averaging the walls first and then against the ceiling would
even that out at a fixed half — rejected, because it weights the ceiling above every wall in the room
on no evidence at all, where the simple rule at least counts observations.

**Four columns are added (V10).** `ceiling_confidence` and `reported_confidence` for the two figures
above; `unusable_photos` and `notes` because §6 acts on both — the first is the whole RECAPTURE branch
and the labels are what make the request specific rather than "retake your photos" (BOYA-51, BOYA-61),
the second is what the operator reads on the review screen (BOYA-53).

Leaving those two in `raw_response` was the alternative, and it fails the rule §4.4 built
`surface_finding` to enforce: the audit trail must not be load-bearing. A value the system acts on is
a column. That is decision 0020's rule about a vocabulary, applied to a field.

**`roomType` and `photoId` are dropped from the domain, not given columns.**

`roomType` is the model's reading of which room it is looking at. `room.room_type` is what the
customer said and §5.3 prices that; §4.4 gives the row no column and is right not to. Asking the model
for it stays worthwhile — a model made to commit to what it is looking at reads the rest of the room
better — and the answer stays in `raw_response` as evidence. What it must not become is a second
answer to a question already answered.

`photoId` was optional in §6's schema and could never have been filled. The model is shown labelled
images and is never told their ids; `surface_finding` has no column for one; and where the field would
have meant something — `WALL_2` — the label already says which frame it was, which is the entire point
of labelling the call (BOYA-47). On `ROOM_GENERAL` it could not mean anything, because that surface is
read from several frames. A field the model has to invent, joined to nothing, is worse than no field:
it looks like a foreign key.

## Consequences

`CeilingFinding` still carries only what the engine prices. The ceiling's confidence sits on
`RoomAnalysis` beside it rather than inside it, so the engine's input has nothing in it the engine
ignores — the rule ADR 0017 wrote that record to protect.

`raw_response` is `jsonb`, so it is a parsed document and not the provider's bytes: whitespace and key
order are normalised on the way in. Fine for comparing prompt versions semantically, which is what
§4.4 wants it for, and worth knowing before somebody diffs two responses and finds a difference no
model made.

One analysis per room, because `room_id` is UNIQUE. A re-analysis after a recapture replaces its
predecessor and takes its surface rows with it — a two-wall reading landing on top of a four-wall one
would otherwise price six. What is lost is the superseded reading; the fact anybody asks for is
"was there a recapture", and `quote_request.recapture_count` records that.

The pattern behind three of the last four decisions is now hard to miss. 0016: a figure declared twice
and never compared. 0017: a column read by nothing. 0020: a vocabulary written in three places and
compared nowhere. Here: four values the response carries and the schema has no room for. Each was
found by building the thing that finally had to use them, and none of them could have failed a test
before that, because nothing claimed to read them.
