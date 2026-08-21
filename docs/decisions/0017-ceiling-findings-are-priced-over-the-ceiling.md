# 17. Ceiling findings are priced over the ceiling

Date: 2026-08-21
Status: accepted

## Context

`room_analysis` has carried `ceiling_staining` and `ceiling_filler` since V1, the baseline migration.
§5.6's quantity table read neither: `PATCH_FILLING` and `STAIN_BLOCK_PRIMER` were both `Σ wallNet`.

So a stained ceiling produced no stain-block primer at all — in the one case where a decorator
certainly primes. A ceiling stain is usually the *reason* to prime; that is what a leak looks like
after it dries. The engine priced the job, returned a number that looked like every other number, and
was short by the whole cost of sealing a ceiling. ADR 0014 recorded this as the open question to put
to the business before launch and named it "a probable underquote on exactly the jobs where a leak is
involved". BOYA-11a is that question.

Looking at it turned up a second hole in the same place, and a worse one. §5.9's `riskFinding` list
reads "any **surface** moisture == ACTIVE". A ceiling is not a surface — `surface_finding` rows are
walls — so an actively leaking ceiling did not trigger a survey either. The system would have taken a
job with water coming through the ceiling and quoted it automatically.

## Decision

Both ceiling findings are priced, over the **ceiling's own area** — the room's floor area from step 1,
with no opening deduction and no coating ratio. A ceiling has no doors and windows to deduct, and a
tiled wall says nothing about what is overhead.

```
PATCH_FILLING       += Σ ceilingArea(r) × fillerRatio(ceiling_filler(r))
STAIN_BLOCK_PRIMER  += Σ ceilingArea(r) where ceiling_staining != NONE
```

**Filler is proportional; stain block is not.** Filler uses the same band table as a wall: a MEDIUM
band means 35% of the plane gets filled, wherever the cracks happen to be. Stain block takes the
**whole** ceiling. A ceiling sealed only where the stain shows comes back through the finish as a
patch — a difference in sheen that is visible from the sofa — so the plane is sealed or it is not.
Charging for a fraction of it would be an underquote with a plausible-looking number on it, which is
the failure this item exists to remove; a quantity that is defensible on paper and wrong on site is
worse than no quantity, because nobody goes looking for it.

**`ceiling_staining == ACTIVE` is a `riskFinding`.** An active leak is not a painting job until
somebody has been up there, so it goes to survey rather than to a price. §5.9's list now says so.
`CeilingFinding.isRisk()` is the predicate; the engine does not call it, because deciding to survey is
not the engine's job — the evaluator (BOYA-51) is. That the predicate exists without a caller is
deliberate: the rule was decided here and there is one place to ask, rather than a sentence in a spec
for somebody to re-derive.

**Stage 1 carries no ceiling findings.** §2.1's eight questions are about walls. Turning `MAJOR`
walls into a ceiling finding would be the engine deciding something nobody told it — bad walls do not
imply a leak overhead — so `RoomInput.declared` is always `CeilingFinding.none()` and stage 1 figures
are unchanged.

**The stage 2 factory takes the ceiling as a parameter it cannot omit.** §5.6 had these two columns
available and read neither for the length of the project, so `CeilingFinding` is its own record and
`RoomInput.analysed` requires one: `CeilingFinding.none()` is a caller saying the ceiling is sound,
never a default that happens to mean it. The mapper that will build these rooms from `room_analysis`
(BOYA-49) cannot forget the ceiling the way the quantity table did.

## Consequences

Jobs with a leak get quoted for the work they need. On a 20 m² room a stained ceiling adds 20 m² of
stain-block primer — at REAL-2026-02, 20.83 TL/m² of labour and 40.00 of material, so roughly 1,200 TL
of cost that used to be absorbed. It is not a large sum, and it is the whole margin on a small job.

`PATCH_FILLING` can now exceed the wall area, which is correct and will look wrong in a review: a
badly cracked ceiling over sound walls produces filler with no wall damage to explain it. The line
carries a quantity and no surface, so an operator reading the breakdown sees the total and not where
it came from. Surfacing the ceiling in the breakdown belongs with the operator review screen
(BOYA-53).

Only ceiling *staining* and *filler* exist as findings. A ceiling that needs a skim coat, or one whose
tone is dark, has nowhere to be reported — `room_analysis` has no such columns, and the prompt is not
asked for them. That is a gap in the analysis, not in the pricing, and it stays open rather than being
guessed at from the two columns that do exist.

The deeper pattern is the same one ADR 0016 recorded, in a different shape: **a column nothing reads
is indistinguishable from a column that reads zero.** `ceiling_staining` was populated, plausible, and
ignored, and no test could fail because nothing claimed to use it. Two of the last three defects found
in this system were of that kind, which suggests the guard worth building is one that lists the
analysis columns the pricing path never touches.
