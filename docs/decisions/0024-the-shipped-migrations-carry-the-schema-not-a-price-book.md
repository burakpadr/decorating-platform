# 24. The shipped migrations carry the schema, not a price book

Date: 2026-09-09
Status: accepted — amends the consequences of
[0010](0010-price-book-versions-are-superseded-never-edited.md)

## Context

The project is open source. That changes who runs `db/migration`: not this business, whose database
was seeded months ago, but a stranger with their own crew, their own city and their own VAT rates.

Eleven migrations shipped. Four of them were data and carried no DDL at all — `V2` seeded
`SEED-2026-01` and **activated it**, `V3` made `REAL-2026-01` operative, `V5` reconciled labour
against the crew rate (0016), `V6` dropped the crew to two. The other seven are schema and carry no
INSERT.

So a stranger's first `make dev-api` produced a database with an active price book: fourteen item
costs derived from market research in İstanbul, a 30% margin nobody chose, two VAT rates a comment
in `V2` marks as needing an accountant. The engine prices from whatever book is active, and it
cannot tell that nobody entered these figures. The only symptom available to that install is that
the prices are strange — and the figures reach a customer before anyone reads a migration header.

This is not a confidentiality problem. The figures are in the git history of a public repository and
the business is content for them to be; what is wrong is a default that quotes real money from
numbers belonging to someone else's business.

`V2` is also where `service_district` gets its 39 İstanbul districts, which is the same defect in a
second dimension: an install in Ankara starts by serving the wrong city.

## Decision

**`classpath:db/migration` holds the schema and nothing else.** The four data migrations move to
`api/src/test/resources/db/fixture`, renumbered out of the production sequence, and are applied only
by the test suite:

| was | is now |
| --- | --- |
| `db/migration/V2__seed_price_book.sql` | `test/resources/db/fixture/V900__seed_price_book.sql` |
| `db/migration/V3__real_price_book.sql` | `test/resources/db/fixture/V901__real_price_book.sql` |
| `db/migration/V5__labour_cost_reconciliation.sql` | `test/resources/db/fixture/V902__labour_cost_reconciliation.sql` |
| `db/migration/V6__crew_of_two.sql` | `test/resources/db/fixture/V903__crew_of_two.sql` |

Version numbers 2, 3, 5 and 6 are now permanently vacant. That is the point: a version already
applied somewhere can never be reused, and the gap is where the history is legible.

**A fresh install therefore has no price book at all**, not even an inactive one. `FreshInstallTest`
is the guard, and it runs its **own** container rather than the shared one — asserted against the
database every other test seeds, "there is no price book" would pass or fail according to test
ordering. It runs Flyway against `classpath:db/migration` explicitly rather than inheriting the
application's configuration, because the claim is about what that location contains.

Two settings follow.

`spring.flyway.locations=classpath:db/migration,classpath:db/fixture` in
`src/test/resources/application.properties`. The suite needs a book to price against, and it needs
*these* four: every expected figure in `PricingEngineTest`, the §5.10 fixture and the operator API
tests reads from what they produce. Moving them changed no assertion — 535 tests passed unchanged,
which is what says the move was a move and not a rewrite.

`spring.flyway.ignore-migration-patterns: "*:future,*:missing"` in `application.yml`. Without
`missing`, a database migrated before this split refuses to start: Flyway finds versions 2, 3, 5 and
6 in `flyway_schema_history` with no file behind them and names each one. Verified in both
directions on the development database — it failed with *"Detected applied migration not resolved
locally: 2"* and, with the pattern set, applied the three pending schema migrations and started,
`REAL-2026-03` still active and `WALL_PAINT` still 31.25.

`future` has to be repeated because it is Flyway's own default and setting the property replaces the
default rather than adding to it. Writing `missing` alone would quietly withdraw the tolerance that
lets a database migrated by a newer release start under an older one.

Flyway's other route — `flyway repair`, which rewrites the history table to mark the rows deleted —
was not taken. The rows those migrations wrote are still in the database and still the operative
price book; a history saying they were never applied would be false.

## Consequences

**This business's install is untouched.** `REAL-2026-03` stays active, the panel prices exactly as
before, and the only visible change is one line in `application.yml`. Nothing is deleted anywhere but
in the repository, and the deleted files remain in the git history.

**A new database now needs setup.** Until BOYA-70 ships there is no screen to enter figures on, so a
dropped volume means restoring from a backup or replaying the fixture by hand. BOYA-69 makes the
gap explicit rather than silent: with no active book the engine refuses to quote instead of pricing
from nothing.

**Importing a prepared price book was considered and rejected** (BOYA-74, cancelled). The wizard asks
for the figures; the reasoning is recorded on that card. What matters here is the part that would
have been wrong in any case: **a file chosen at setup time cannot be a Flyway migration.** Flyway
applies everything in its locations at startup, before a login screen exists, so nothing can be
chosen; it records a checksum forever, so an installer's file would conflict with the repository's
history; and arbitrary SQL is arbitrary code run against the installer's database. Price book
versioning does not live in Flyway. It lives in `price_book.version_code`, superseded and never
edited (0010), and that is where an imported or entered book takes its place.

**0010's reference to `V3__real_price_book.sql` now points at a fixture.** The decision it records is
unaffected — a coefficient change still creates a version rather than editing one — but the file it
names has moved, and the table above is where to look.

**The guard that 0016 installed is now guarding a fixture.** `PriceBookIntegrityTest` asserts against
whatever book is active in the test database, and that book is one we write, so its business-specific
assertions — a painter costs 2,500 TL a day, the active book is not the placeholder seed — have
stopped being evidence about anything. The invariant they exist for still matters, so it moves to
where every book has to pass it, entered or imported: **activation refuses a version whose item
labour costs do not reconcile with its own crew rate** (BOYA-20a). Until that lands, the reconciling
figures are checked only against the fixture, and that is a real, temporary loss of coverage rather
than a bookkeeping detail.

**A moved migration lingers in `target/`.** Maven does not remove a deleted resource from
`target/classes` without `clean`, so the first run of `FreshInstallTest` after the move still found
four price books — it was reading the previous build's output. `./mvnw clean test` is what proved the
split; CI builds clean, so this misleads locally only.

**`districts.spec.ts` reads the fixture now.** The frontend's `DISTRICTS` array duplicates
`service_district` deliberately, for prerendering the 39 SEO pages, and that test is what keeps the
two in step. It followed the file. When setup owns the district list (BOYA-71) the comparison moves
with it.
