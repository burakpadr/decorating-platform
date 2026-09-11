# 25. An install that was never set up refuses to quote

Date: 2026-09-11
Status: accepted — the other half of
[0024](0024-the-shipped-migrations-carry-the-schema-not-a-price-book.md)

## Context

0024 took the price book out of `db/migration`, so a fresh clone now migrates to a complete schema
with no `price_book` row in it. That closed one hole and opened the next one: nothing asked what
happens when a request for a price arrives anyway.

What happened was `Optional.orElseThrow()`. Stage 1 threw a bare `NoSuchElementException`, the
operator's calculator threw an `IllegalStateException` with a good sentence that no handler was
scoped to, and stage 2 threw a third one. All three surfaced as **500**, which says "this server is
broken" — so the panel could not tell an unconfigured install from a failure, and the customer end
showed a generic error on a screen whose whole job is to be trusted.

§7 lists the operator API and has nothing for this. It was written for an install that had always
been seeded by a migration, where "not set up" was not a state the system could be in.

## Decision

**A missing active price book is a refusal with a type, not an exception nobody caught.**
`SetupIncomplete` carries a `SetupStatus`, and both pricing paths of §5.1 throw it:
`CalculateEstimate` (stage 1 and the operator's own tool, which share one use case) and
`GenerateQuote`.

**503, not 422.** ADR-free precedent existed in both directions: `DistrictNotServed` is 422 because
the request is well formed and the answer is a real answer about the customer's area. This is not
about the customer at all. Nothing they send would help, and nothing they can do will change it —
the service is not ready. 503 is the status that says exactly that, and it tells a client not to
treat the answer as final.

**The customer's refusal carries the urn and nothing else.** `urn:decorating:not-set-up` is what lets
the screen say the service is not available yet, rather than matching on a Turkish sentence that will
be reworded — the arrangement BOYA-27 settled for the waitlist screen. The list of what is missing is
a list of this install's unentered coefficients: a visitor can do nothing with it, and §1's line puts
internal figures on the operator's side.

**The operator's refusal carries the list**, because the panel's next move is to open the setup
wizard (BOYA-70) and it should not have to ask a second time to find out what to ask for.

**A new endpoint §7 does not list: `GET /api/op/setup/status`.** Like the two ADR 0015 added, and for
the same reason: the panel has to decide what to render *before* the operator does anything. Learning
that an install is empty from a 503 after typing in a whole job is a worse way to find out — the same
argument that put `editable` in the price book detail response.

**What "set up" means is five things, and they are the money and the locale**: an active version at
all, at least one district switched on, both VAT rates, and a margin. The engineering coefficients of
§5.3–5.7 ship as structural defaults and are not asked for (BOYA-70), so none of them is on the list.

**With no version, all five are reported, not just `PRICE_BOOK`.** The list answers "what does setup
still have to supply", which is what the wizard asks for either way. Reporting one entry would leave
every client to derive the other four from it, and one of them eventually would not.

**The rule is pure.** `SetupStatus.of` takes an `Optional<PriceBook>` and returns the list, with no
Spring and no database, because it is the gate in front of every price a fresh clone could produce —
the same reason `PricingEngine` and `ConfidenceEvaluator` are pure. The endpoint and the three
refusals read the same rule, so an install cannot be quotable and incomplete at the same time.

## Consequences

**Zero reads as "nobody entered it".** For the VAT rates and the margin there is no other signal:
`labour_vat_rate` and `material_vat_rate` are `NOT NULL` with no default, so a row that exists was
written by somebody, but `margin_ratio` has `DEFAULT 0.30` and an insert that never mentioned it is
indistinguishable from one that chose it. The alternative — making the columns nullable to record
that they were never typed — would put a null in front of every pricing path in the system. So the
check is weak on purpose, and it is the honest weakness rather than a false precision.

**It does not check whether the figures are *this business's* figures.** It cannot. §5.11's costs are
market-derived placeholders that look exactly like real ones, and a margin of 0.30 is both a real
decision and the schema's default. That question stays where it already lives — Phase 0, BOYA-1 and
BOYA-8 — and increment 1 still ships with an unvalidated price list.

**A version that is active but incomplete still prices.** The refusal is keyed to absence, not to the
list being non-empty: a version that exists is one somebody activated, and a book with fourteen items
and no served district will fail the district check of stage 1 anyway. Refusing on the list would
make `setup/status` a second, quieter gate on a system already running. Guarding what may be
activated is BOYA-20a's, and that is where a missing item code belongs too — `PriceBook.item` throws
for one today, which this check deliberately does not duplicate.

**The analysis poller now fails its job on an unconfigured install** rather than throwing an
unhandled `IllegalStateException`, which is the same outcome by a name the log can explain. Nothing
else changes: a request whose quote cannot be generated stays in `ANALYZING`, which is the gap
BOYA-51 already recorded.

**The contract grew one path and one schema.** `make client` regenerates it, and `MissingSetting`
reaches the frontend as a union of five string literals — so the wizard's question list and the
backend's check cannot drift without the TypeScript build saying so.
