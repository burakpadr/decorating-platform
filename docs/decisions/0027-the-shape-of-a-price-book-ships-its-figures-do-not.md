# 27. The shape of a price book ships; its figures do not

Date: 2026-09-12
Status: accepted — makes
[0024](0024-the-shipped-migrations-carry-the-schema-not-a-price-book.md) installable, and extends
[0026](0026-a-version-is-checked-when-it-goes-live.md)

## Context

ADR 0024 took the price book out of the migrations, so a fresh clone has no `price_book` row. ADR 0026
put a gate in front of activation. Between the two, setup (BOYA-70) had nothing to stand on:

- **There was no way to make the first version.** The only creation path was
  `createVersionFrom(sourceId, …)`, which copies — `INSERT … SELECT … FROM price_book WHERE id = ?`.
  On an install with no version there is nothing to select from.
- **There was no way to enter a district.** The single `INSERT INTO service_district` in the codebase
  lives inside that copy. No port method, no endpoint. A fresh install would have had a price book and
  nowhere it works.

So the wizard could not have been built, and the card's acceptance criterion — a first quote on a
clean clone without writing SQL — was unreachable.

## Decision

**A versioned resource holds the shape, and only the shape.**
`price-book/structure-v1.json` carries §5.3–5.7's engineering: how a home's area is shared between
room types, how much wall a room of that shape has, what each modifier covers, and each item's unit
and duration. It carries **no money**. Decision 0006's arrangement, for its reason: a released file is
never edited in place, because an install that ran against `v1` produced a book somebody is quoting
from.

Durations are in the file and material costs are not, which is worth saying plainly. A duration is an
engineering estimate — §5.11's, and wrong for this business until BOYA-1 corrects it item by item. A
material cost is money, and money is asked for.

**`POST /api/op/price-books/from-structure`** builds a version from that file: every item, modifier
and room type, all money columns at zero, inactive, no districts.

- **Not "the first version".** An earlier draft refused the call when any version existed, which is
  what the name would imply. The rule would have existed to stop a version with no money in it
  reaching customers, and that is what activation already refuses. One guard in the right place beats
  two, and the restriction would have made the happy path untestable against the shared database.
- **The money columns are written as explicit zeros**, not left to the schema. `margin_ratio` defaults
  to 0.30 and `average_job_value` to 25,000 TL — both prices, arriving from a migration, which is the
  thing 0024 exists to prevent. They do no harm today only because a fresh install has no row for the
  defaults to apply to; the moment setup inserts one they would be real.
- **Zero is consistent all the way down.** BOYA-69 already reads zero as "nobody entered it". A crew
  rate of zero makes every item's derived labour zero, so the version satisfies 0026's reconciliation
  rule from its first row and keeps satisfying it as the wizard fills it in.

**Activation also refuses money still at zero** (`MONEY_NOT_ENTERED`): crew day cost, margin, and both
VAT rates. Without this the version above could go live and price every job at cost with no VAT —
and it would pass every other check, because a book at zero is internally consistent.

**`PUT /api/op/price-books/{id}/districts`** replaces the whole list on a version nothing has been
priced with.

- **The whole list, not one district at a time.** It answers one question — where does this business
  work — and a partial update makes it possible to open an area while leaving another open by
  accident.
- **Closing a district is therefore a new version**, like every other price book change. §4.5's
  promise covers the factor a quote was priced with as much as it covers the item costs.
- **Duplicates are caught before the write**, naming the district. The unique constraint would catch
  them too, but an operator who typed the same area twice needs to be told which one, not shown a
  constraint name.
- **The factor is bounded 0.50–3.00** and the display name may not be blank: a factor of 12 is a
  mistyped 1.2, and a blank name is a row the customer cannot pick from the list.

## Consequences

**`ServiceDistrict.validate()` is called on the way in, not from the constructor.** The same type is
how districts are read back, and a row already in the database has to stay loadable if a rule tightens
later — the reason BOYA-23 trusts a stored status rather than re-validating it.

**The operator's version detail now carries the districts**, with their factors, sorted by Turkish
collation. The customer's list still does not carry factors (BOYA-26): this is the other side of §1's
line.

**The suite gained a test that reads the defaults file as text.** Asserting through the parser would
miss a money field the loader happens to ignore, and that field would still be a published price. It
fails on any number over 100 with two decimals, and on field names containing `cost`, `VatRate` or
`marginRatio`. Both halves were verified by putting a wage in the file.

**What is still missing for the wizard**: nothing on the API side. The screens are BOYA-70, and
BOYA-71's remaining half — the 39 Istanbul districts becoming sample data, and the prerendered SEO
routes deriving their list from setup rather than from a constant in the frontend.

**`average_job_value` has no way in.** Setup writes it as zero, which sends every low-confidence job
to a survey — the safe direction, and what §6 already does when the figure is missing. Nobody asks the
business for it, and BOYA-70's question list does not mention it. Worth a decision before launch:
either the wizard asks, or it stays at zero until BOYA-2b derives it from finished jobs.
