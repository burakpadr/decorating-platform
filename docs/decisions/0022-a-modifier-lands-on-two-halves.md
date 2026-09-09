# 22. A modifier lands on two halves, so the row records two

Date: 2026-09-09
Status: accepted

## Context

§4.6 gives `quote_line_item` an `applied_modifiers` column and says exactly what it is for: it
"answers 'why does this line have a 1.5 factor' six months later". Its example is one object per
modifier with one number in it:

```json
[{"code":"FURNISHED","factor":1.25}]
```

BOYA-50 is the first thing that writes the column, and the example cannot be filled in. `FURNISHED` —
the modifier the spec chose to illustrate it — applies to labour and not to material. That is §5.7's
most consequential rule and the engine has a test for it: a furnished home takes more time and exactly
as much paint, so applying the surcharge to materials overprices furnished jobs systematically. A
single `factor` of 1.25 on a `WALL_PAINT` line is therefore either wrong about the paint or wrong about
the labour, whichever half the reader assumes.

## Decision

**Both halves are recorded.** `AppliedModifier` carries `labourFactor` and `materialFactor`, and the
column is written as

```json
[{"code":"FURNISHED","labour":1.2500,"material":1.0000}]
```

`1.0000` is a modifier that covered this line and moved this half by nothing, which is information —
it distinguishes "does not apply here" from "applied and cancelled out". Modifiers that moved neither
half are left out entirely: the column exists to be read when somebody is looking for a reason, and
noise in it is the reason nobody looks twice.

Factors are recorded at four decimals, the scale §4.5 stores every ratio at. The record is not the
multiplier — the engine multiplies by the unrounded factor and records here — so the rounding costs
no accuracy and buys a column that lines up when read.

**The factor is recorded rather than looked up.** `DARK_TO_LIGHT` on walls is the case that settles
it: ADR 0014 scales it by how much of the wall area was dark, so the price book's 1.30 is not the
number the line was multiplied by. And the price book of six months from now is a different version
anyway, which is the whole premise of §4.5.

**`labour_minutes` is filled the same way** and for the same reason. §4.6 has the column, the engine
was already computing the figure inside its loop, and it is the answer to the other question a total
provokes: which line makes this a three-day job. Mobilization carries zero, because §5.2 puts step 9
outside the loop that counts minutes — it is the crew arriving, not the crew working.

## Consequences

Two divergences from §4.6's text, both narrow: the object has `labour` and `material` where the spec
shows `factor`, and it lists only modifiers that moved something. Anything reading this column reads
it from this record, so the shape is not a public contract — but it is written down here because the
spec's example is what a reader will reach for first.

`QuoteLine` grew two fields and `PricedQuote` grew two more (§4.6's `total_wall_sqm` and
`total_ceiling_sqm`, which only steps 1–5 can know). All four existed inside the engine already and
were discarded on the way out; the 56 pricing tests passed unchanged through the refactor that stopped
discarding them, which is the evidence that nothing about the arithmetic moved.

The pattern is the same one 0021 closed with, now four decisions long: **the row cannot hold what the
code threw away.** 0016 was a figure declared twice and never compared; 0017 a column read by nothing;
0020 a vocabulary written in three places; 0021 four values the response carried and the table had no
room for. Here it is a column the schema asked for that nothing could fill. Each was found by building
the first thing that actually had to use it.
