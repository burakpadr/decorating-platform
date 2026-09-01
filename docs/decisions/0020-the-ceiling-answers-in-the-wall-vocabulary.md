# 20. The ceiling answers in the wall's vocabulary

Date: 2026-09-01
Status: accepted

## Context

ADR 0017 made `ceiling_staining == ACTIVE` a §5.9 risk finding and wrote `CeilingFinding.isRisk()` as
the predicate, deliberately without a caller: the rule was decided there so that BOYA-51 would have one
place to ask rather than a sentence to re-derive.

Wiring the vision port up (BOYA-47) is the first time anything had to produce that value, and it
cannot. `prompts/room-analysis/schema.json` offered `NONE | LIGHT | HEAVY` for `ceiling.staining`, and
§6's published example answered `LIGHT`. A model conforming to that schema has no way to say `ACTIVE`,
so `isRisk()` is false for every response it is ever given, and a ceiling with water coming through it
gets a price instead of a survey — the exact failure ADR 0017 was written to close, still open,
now with a predicate over it that reads as though it were closed.

Three lists spell this vocabulary: the response schema, the domain enum the answer is mapped onto, and
the CHECK constraint the row is written under. Nothing compared them. `surface_finding` got a CHECK for
every enumerated column in V1; `room_analysis` got none, so the one column where the schema and the
domain disagreed was also the one column the database would have accepted anything in.

The two lists are not a typo for each other. They answer different questions. `LIGHT | HEAVY` is a
severity — how much of a mark is up there. `STAIN | ACTIVE` is an activity — whether the water has
stopped. Both are reasonable things to ask a model; only one of them is what the two consumers use.
§5.6 asks "is it stained at all" (`!= NONE`, whole-ceiling stain block, ADR 0017) and §5.9 asks "is it
still wet" (`== ACTIVE`). Neither asks how big the mark is.

## Decision

**The ceiling reports `NONE | STAIN | ACTIVE`** — the same three words `surface_finding.moisture` uses,
because it is the same physical question about a different plane: dry, once wet, wet now. The schema,
§6's example, `v1.md` and the domain's `CeilingFinding(Moisture, FillerBand)` now say one thing.

Severity is dropped rather than carried. A `HEAVY` that nothing reads is worse than no column at all —
that is the pattern ADR 0017 closed with, and adding a fourth value to preserve information no
consumer wants would reopen it. If the size of the mark turns out to matter, it is a new field with a
reader, not a value smuggled into this one.

Mapping the old vocabulary onto the new was the alternative and it is the dangerous one: `HEAVY →
ACTIVE` reads plausibly and is false. A heavy stain is usually an old leak that has long since dried,
and a fixed roof is precisely the case that should *not* be sent to a survey. Sending it anyway would
be a guess with a survey visit attached to it.

**`room_analysis` gets the CHECK constraints V1 left off** (V9): `furnishing`, `ceiling_staining` and
`ceiling_filler`. BOYA-22a's lesson was that a rule written and not enforced is a rule that drifts;
this is the same lesson one table over.

**`RoomAnalysisSchemaTest` compares all three lists** — schema against domain enum, schema against
migration CHECK — for every enumerated field in the response, not only the ceiling. That test is the
actual deliverable here. The vocabulary was wrong for the length of the project and no test could fail,
because each of the three files was internally consistent and nothing read two of them at once.

**`v1.md` is edited in place rather than superseded by `v2.md`.** The rule against editing a released
prompt (ADR 0006, `§4.4`) exists because `room_analysis.prompt_version` makes a prompt an input to
persisted rows, in the way a price book version is an input to persisted quotes. No row references
`v1`: nothing has ever loaded the file, and `room_analysis` is empty in every environment because the
adapter that writes it is what this ticket is building. There is no history to rewrite. A `v2` here
would mean a version number whose only difference from `v1` is a value nobody could ever have received.
The rule takes effect with the first row.

## Consequences

An actively leaking ceiling goes to a survey, which is what ADR 0017 decided and what §5.9 has claimed
since. `CeilingFinding.isRisk()` now has a value that can make it true; it still has no caller, and
BOYA-51 is still the caller it is waiting for.

A stained-but-dry ceiling is priced, and gets its whole plane of stain-block primer. That is the
common case and it is unchanged by this: §5.6 asks `!= NONE`, which `STAIN` and `ACTIVE` both satisfy.

The three-way agreement test constrains future changes in a way worth stating plainly: adding a value
to any of these vocabularies now means touching the schema, the enum and a migration in the same
commit, or the build fails. That is the intent. The cost is that a genuinely one-sided change — a
response field the database does not store — has to be expressed as one, by not being in this test.
