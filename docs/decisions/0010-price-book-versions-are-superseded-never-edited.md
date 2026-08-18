# 10. Price book versions are superseded, never edited

Date: 2026-08-18
Status: accepted

## Context

BOYA-1 is the Phase 0 work item that replaces the price book's market-derived placeholders with the
business's real item costs (spec §15, workflow §12). Its acceptance criterion, as written on the
board, says:

> V2__seed_price_book.sql'deki piyasa rakamları gerçek maliyetlerle değiştirilir.

Taken literally that means editing `V2__seed_price_book.sql` in place. Two things stop it.

**Flyway has the file's checksum on record.** V2 has already been applied to the local development
database — `flyway_schema_history` carries checksum `1566535549` for it. Any edit to that file,
including a comment, fails validation on the next startup. The failure would also be invisible to
CI, because the Maven suite runs against a Testcontainers database that is created fresh on every
run and therefore never sees a mismatch. Green pipeline, broken developer machine.

**Superseded versions have to survive to be read.** A quote records the `price_book_id` it was
computed with, precisely so a coefficient change does not retroactively alter quotes already sent
(spec §4.5, and V2's own header). Editing a version in place breaks that guarantee for every quote
pointing at it: the figures behind a quote the customer is holding would silently become different
figures. `SEED-2026-01` is also what the §5.10 worked example was derived from, so it has a second
life as the regression fixture for `PricingEngine`.

## Decision

A price book version is immutable once its migration has been applied. New figures arrive as a new
version, which is activated while its predecessor is deactivated and kept.

BOYA-1 is therefore satisfied by `V3__real_price_book.sql`, which creates `REAL-2026-01` and
deactivates `SEED-2026-01`. The board card's wording should be corrected to match.

`REAL-2026-01` carries the same market-derived figures V2 seeded, adopted as the operative cost
list. The name describes the version's role — the book quotes are priced against — not the
provenance of its numbers, and the migration says so at the top. Recording this matters more than it
looks: the figures are market research, so a margin comparison against them measures the market and
not this business, and the calibration pass Phase 2 describes has to run against real `job_outcome`
rows before any coefficient tuned here means anything.

A version is self-contained: `price_book_item`, `price_modifier`, `room_type_config` and
`service_district` all hang off `price_book_id`, so a new version carries all four or prices
nothing. `PriceBookIntegrityTest` enforces that, along with the completeness of the item codes §5.6
looks up.

Routine revisions after launch — the quarterly increase workflow §6 describes — go through the
operator UI, which produces new versions the same way. Migrations are for the initial handover only.

## Consequences

The one active price book is found by `active = true`, guarded by `price_book_single_active_idx` and
asserted by the test; nothing reads a version by name.

`REAL-2026-01` carries forward several values that are still placeholders, each waiting on its own
work item: `labour_vat_rate` and `material_vat_rate` (BOYA-3, accountant), `crew_day_cost`,
`margin_ratio`, `survey_amount_factor`, and every `district_factor`. Carrying them forward unchanged
is deliberate — inventing a value to make a version look finished is worse than an honest
placeholder, and each has a ticket that will supersede this version again.

Versions accumulate. That is the point, and at the rate the workflow document expects — a revision
roughly quarterly — the table stays small enough that nobody needs to prune it.
