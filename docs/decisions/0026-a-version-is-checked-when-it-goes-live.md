# 26. A version is checked when it goes live, not when it is written

Date: 2026-09-12
Status: accepted — moves the guard installed by
[0016](0016-item-labour-cost-is-derived-from-minutes-not-entered.md) after
[0024](0024-the-shipped-migrations-carry-the-schema-not-a-price-book.md) took its subject away

## Context

ADR 0016 found a price book stating the cost of labour twice — once as `crew_day_cost`, once as a TL
figure on every item — with the two disagreeing by up to 3.3×. The guard it installed was
`PriceBookIntegrityTest`, a schema test asserting that the **active** book's item labour reconciles
with its own crew rate.

That test read the active book out of the migrations. ADR 0024 took the price book out of the
migrations, so the book it reads is now a test fixture we write ourselves, and the assertion has
stopped being evidence about anything a customer could be quoted from. 0024 said so at the time and
named this card as where the guarantee would go.

Two things made the move urgent rather than tidy. A fresh install's first price book now comes from
the setup wizard (BOYA-70) rather than from a migration nobody but us writes. And the panel could
still not edit a coefficient at all: the day a painter's wage changed, the only available move was a
percentage increase, and a ceiling height of 2.90 instead of 2.70 needed a hand-written migration.

## Decision

**The check runs at activation.** `ActivationCheck` takes a `PriceBook` and returns the list of
reasons it may not go live: a missing item code, a missing room type, or an item whose labour
contradicts the version's own crew rate. `activate` refuses with `PriceBookNotActivatable`, which
carries the list.

**Activation, not every write.** An inactive version is allowed to be half-finished — the panel and
the wizard both build one field at a time, and a check on write would make the first field an error.
Activation is the moment a version stops being a draft somebody is editing and starts answering
customers, which is the moment worth gating.

**Missing codes are checked alongside the labour rule** (the gap BOYA-69 recorded). Both are defects
the engine meets at pricing time against a real customer; the difference is that a missing code
throws, which is loud, while an unreconciled labour figure produces a plausible number whose only
symptom is that the prices are strange. The quiet one is the reason for the gate, and having found
the right place for it, the loud one costs three lines.

**It is pure**, like `PricingEngine` and `ConfidenceEvaluator`, and for their reason: this is the gate
in front of every price a version would go on to produce, so every branch has to be drivable from a
test with no database.

**Coefficients are edited ten at a time**, on a version nothing has been priced with, and **every
item's labour is re-derived** from the new crew rate in the same write. `PriceBookCoefficients` is a
record with all ten fields and its own bounds.

- **All ten together, not a patch.** Crew size and crew day cost are halves of one figure — the cost
  of a person for a day. Sending one without the other silently rewrites every item's labour, because
  0016 derives labour from `crewDayCost / (crewSize × hours × 60)`. Once the request has to carry
  both, carrying the rest costs nothing.
- **Bounds on the record, not on the DTO.** They belong with the figures they describe, and a second
  copy on the web type is a second place for them to drift. They are deliberately wide: they catch a
  decimal point in the wrong place (a 27-metre ceiling, a VAT rate of 20), not a decision somebody
  made on purpose.
- **Re-derivation lives in the write.** The adapter updates the coefficients and re-derives the items
  in the same statement pair, using the same SQL expression the bulk increase uses. A caller that had
  to remember the second half would eventually not.
- **No edit-the-live-version.** §4.5's promise is that a figure a customer was shown stays
  explainable, so the shape is the one items already use: copy, edit the copy, activate.

**What is not editable**: the engineering constants of §5.3–5.7 — room type weights, the corridor's
perimeter factor, opening areas, the day rounding tolerance, the base band ratio. The wizard ships
those as structural defaults and does not ask for them (BOYA-70); exposing them would invite a change
nobody could calibrate against anything.

## Consequences

**Multiply first, divide last, round once.** The check has to perform the same arithmetic the database
does or the two disagree wherever a figure lands on exactly half a kuruş — and the live book has two
of them: `PRIMER`'s three minutes come to 15.625 and `CORNICE_CUTTING`'s forty-five to 234.375.
Deriving a per-minute rate first and multiplying by it turns both into …49, and refuses a version that
is perfectly consistent. The first implementation did precisely that, with a comment explaining why it
was correct; the wiring test caught it against the real database on the first run.

**A test that was asserting through an invalid state had to change.**
`PriceBookVersionManagementTest` proved "a quote already sent keeps its figures" by writing
`labour_cost = 99.00` straight into a copy and activating it — which is exactly the row 0016 exists to
prevent, and is now refused. It raises the duration through the use case instead: six person-minutes
at 31.25 TL become twelve at 62.50, by the same arithmetic the database uses. The test's subject is
unchanged.

**`PriceBookIntegrityTest` keeps its reconciliation test**, now as a fixture check rather than a
guarantee. The whole suite prices against that fixture, so a fixture that drifted would quietly move
every pricing assertion in the project. Its javadoc points here for the real guard.

**The bulk increase and item editing already satisfied the rule**, so nothing about them changes: both
derive labour rather than accepting it. What the gate adds is coverage of the paths that do not go
through them — the setup wizard, and anything written by hand.

**§5.11's published table cannot be activated**, which is worth stating plainly: the table this
project shipped with fails the check on all fourteen items. That is 0016's finding, now enforced
rather than recorded, and `ActivationCheckTest` asserts it against the fixture on purpose.
