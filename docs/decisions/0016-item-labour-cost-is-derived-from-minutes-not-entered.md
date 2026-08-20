# 16. Item labour cost is derived from minutes, not entered

Date: 2026-08-20
Status: accepted

## Context

A price book version states the cost of labour twice.

Once for the crew, in §5.11's coefficients: `crew_size` 3, `crew_hours_per_day` 8,
`crew_day_cost`. And once per item, as `labour_cost` in TL alongside `labour_minutes`. The engine
uses both, in different steps: item labour costs are what step 6 multiplies quantities by, while
`crew_day_cost` only appears in step 11's minimum.

Nothing in the schema, the spec or the build compared them. Both describe one crew doing one job's
minutes, so they are the same statement twice, and REAL-2026-01's two copies disagreed:

| item | TL/unit | minutes | implied person-day | book says |
|---|---|---|---|---|
| `WALL_PAINT` | 62.00 | 6 | 4,960 TL | 1,500 TL |
| `CEILING_PAINT` | 70.00 | 8 | 4,200 TL | 1,500 TL |
| `DOOR_PAINT` | 350.00 | 55 | 3,055 TL | 1,500 TL |
| `MASKING` | 115.00 | 25 | 2,208 TL | 1,500 TL |
| `WALLPAPER_STRIPPING` | 48.00 | 14 | 1,646 TL | 1,500 TL |

Every one of the fourteen items was off, `WALL_PAINT` — around half of a typical bill — worst at
3.3x. On a 3+1 flat of 92 m² net, 7.7 person-days of work, the engine billed 33,321 TL of labour
for a crew that costs 13,500 TL. The minimum never bound, because the items were always above it,
so the one figure that could have caught the drift was structurally unreachable.

V3's header had already written the rule down — "Costs, not sale prices: margin is applied at
pricing step 12, so a figure that already contains margin gets marked up twice" and "labour_minutes
are PERSON-minutes". The figures broke it anyway, which is what an unenforced rule does. It
surfaced only when an operator priced a real job on `/op/teklif-hesapla` and said the answer was
about twice what the business charges.

Two readings of "a figure above the crew rate" are possible and both are defects: margin already
baked into a cost that step 12 marks up again, or a genuine sale price filed in a cost column.
Neither is recoverable from the data, so the direction of the correction is not a judgement call —
the crew rate is the figure the business can state and check, so it is the one that survives.

## Decision

**`labour_cost` is derived, never entered.** For every item in every version:

```
labour_cost = round(labour_minutes × crew_day_cost / (crew_size × crew_hours_per_day × 60), 2)
```

Three consequences follow.

`V5__labour_cost_reconciliation.sql` computes the column with that expression rather than
transcribing values, so REAL-2026-02 satisfies the rule by construction. A version cannot be
written whose items disagree with its own crew rate.

`PriceBookIntegrityTest#activeItemLabourCostsReconcileWithTheCrewDayCost` asserts it against the
active book on every build. It failed on all fourteen of REAL-2026-01's items before V5 and passes
after — the rule was proved in both directions on real data, not on a fixture.

Item-level editing through the panel (ADR 0015's `PUT` endpoint) can therefore no longer accept a
free-hand `labourCost`. What an operator changes is **minutes** — how long the work takes — and the
money follows. Changing the price of labour means changing `crew_day_cost`, which is a
version-level coefficient and today has no endpoint at all; that gap is now load-bearing rather
than cosmetic.

`MOBILIZATION` is the one item whose TL figure was never purely crew time: its 1,900 TL covers the
van, fuel and travel. It is split — 60 minutes of crew time as labour, the remainder as material —
which keeps the total at 1,900 and lets the invariant hold everywhere without an exemption. An
exemption list would have been the beginning of the same problem again.

## Consequences

REAL-2026-01 stays intact and inactive, per ADR 0010. Quotes priced against it keep their figures;
nothing is retroactively corrected, and the record of what was quoted stays readable.

§5.11's illustrative table violates the invariant, and so does SEED-2026-01, which was transcribed
from it. Both stay as they are: the seed is the fixture §5.10's worked example is derived from, and
the engine's regression tests are anchored to it. The test targets the **active** book only. The
spec's example is now known to be internally inconsistent — worth flagging upstream, but it is an
illustration, and rewriting it would invalidate 45 engine tests to no benefit.

The correction moves a quote a long way. The 3+1 flat above went from 74,010 TL to roughly 46,600
TL with the crew rate the business actually gave (2,500 TL per person-day, so `crew_day_cost`
7,500) and paint at 22 TL/m². Four items went **up** — `PATCH_FILLING`, `SKIM_COAT`,
`WALLPAPER_STRIPPING`, `MASKING` were priced below the crew rate, meaning unbillable time. That the
correction is not a uniform discount is the evidence it is an alignment and not a haggle.

Paint material at 22.00 TL/m² is derived from coverage (~0.18 l/m² for two coats, ~120 TL/l), not
from an invoice. It replaces 38.00, which implied ~210 TL/l. This is the weakest figure in
REAL-2026-02 and is flagged as provisional in the migration header.

The deeper lesson is about the shape of the data, not these numbers: **two columns that must agree
will not, unless the build compares them.** The schema permits the disagreement, so the same class
of defect is available anywhere else a figure is stated twice — `gross_to_net_ratio` against
declared areas, `survey_amount_factor` against real survey durations. ADR 0012 already committed to
building the record forward; the invariant tests are what make that record able to contradict the
price book.
