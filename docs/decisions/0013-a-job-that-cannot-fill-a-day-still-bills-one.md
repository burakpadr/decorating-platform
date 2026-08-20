# 13. A job that cannot fill a day still bills one

Date: 2026-08-20
Status: accepted

## Context

§5.8 sets the minimum:

```
billableDays = ceil(days − dayRoundingTolerance)
minimumCost  = billableDays × crewDayCost
totalCost    = max(Σ lineCosts, minimumCost)
```

The tolerance is explained in the spec and the reasoning is sound: rounding 1.1 days up to 2 is
unfair to the customer, but 1.4 must round to 2, because a crew will not take another job for half a
day.

Applied literally it has a hole at the bottom. A 6 m² bathroom in good condition comes to roughly 100
person-minutes — 0.07 days — so `ceil(0.07 − 0.25)` is **zero** billable days, `minimumCost` is zero,
and `max(Σ lineCosts, 0)` is just the line costs. The minimum cannot bind on exactly the jobs it
exists for. Workflow §12 asks the business for a "minimum iş tutarı — küçük işlerde taban", and §5.8
is the only mechanism in the schema that could be one.

Two hours of work also does not mean two hours of cost. The crew travels, unloads, masks, paints,
waits for it to dry, and comes back. The day is gone.

## Decision

`billableDays` is `max(1, ceil(days − tolerance))` for any job with at least one line. Zero is
reserved for a quote with nothing in it, which the engine rejects earlier anyway.

So the floor for any job at all is one crew day of cost — 4,500 at the seeded figure, 5,850 after
margin — and `PricingEngineTest#minimumBindsOnASmallJob` pins it.

The "minimum iş tutarı" the workflow asks for is therefore expressed as one crew day rather than as a
column of its own. If the business wants a floor that is not a crew day, that is a new column and a
new decision, not a coefficient bent into shape.

### Also recorded: `doorCountEstimated`

§5.9 adds 0.03 to the band when the door count was estimated rather than counted. §5.1's input record
has no field that can say so, and a band term whose input cannot be expressed never fires. It is
carried on `PricingInput` as the narrowest fix. The two sections disagree and one of them is wrong;
this is the reading that keeps §5.9 operative.

## Consequences

Small jobs are quoted at a day's cost, which is what the business would charge and what a customer
expects to hear. It also means the engine cannot produce a quote below one crew day plus margin —
worth knowing before someone reads a 900 TL quote as a bug.

## Two things §5.10 settles, recorded so nobody "fixes" them

Neither is a divergence — the worked example is part of the spec, and both follow from §5.2's step
order — but both look wrong to a reader who only has §5.7 in front of them:

- **Mobilization is not touched by the labour modifiers, and its minutes are not in the duration.**
  §5.2 adds it at step 9, after the labour modifiers of step 7. A furnished home does not pay 25% more
  to have the van loaded. §5.10's 6,699 furnishing delta and its 3,293 minutes only reconcile this
  way; both are asserted.
- **`DARK_TO_LIGHT` scales labour minutes as well as money.** It targets `BOTH` because it means a
  third coat, and §5.8 multiplies minutes by the labour modifiers — of which it is one. §5.10's door
  line (8 × 500 × 1.50) and its minute total agree with each other only if it does.
