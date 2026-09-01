# 19. The customer id travels back as an event

Date: 2026-09-01
Status: accepted

## Context

Workflow §3.1 verifies a phone number, and §4.1 says a `customer` row exists only from that moment:
the table's own comment reads *"A row is created only on successful OTP verification."* Before it, an
unproved number lives in `quote_request.pending_phone`, which the schema describes as
*"pre-verification contact (moved to customer on verify, then nulled)"*.

So one act has to produce two writes in two modules. The OTP belongs to `quoting`: §7 puts
`/api/otp/*` in the anonymous realm, the code is bound to a `quote_request`, and the session cookie
that authorises it is the one that owns the draft. The `customer` row belongs to `customer`, which
owns that table. And afterwards `quote_request.customer_id` has to hold the id of the row the other
module just made.

Decision 0005 forbids the obvious implementation. `ArchitectureRulesTest` fails the build if `quoting`
imports anything of `customer`'s but its `domain/event` package — so `quoting` cannot call
`IdentifyCustomer`, and `customer` cannot reach into `quote_request` to fill the column in itself. The
rule was verified by deliberately violating it when it was written, and it is not negotiable for
convenience.

There is a second constraint that rules out the easy way round. §2.4's fifth rule says an event may
carry ids and `shared` value objects only. A `CustomerIdentified` that carried a `Customer` would let
a subscriber reach the whole module through it, which is the seam defeated by a different route.

## Decision

The two modules exchange two events, and the id makes a round trip.

```
quoting   verify()                 → publishes PhoneVerified(quoteRequestId, phone)
customer  onPhoneVerified          → finds or creates the row
                                   → publishes CustomerIdentified(customerId, quoteRequestId)
quoting   onCustomerIdentified     → writes quote_request.customer_id, clears pending_phone
```

`PhoneVerified` carries a `PhoneNumber`, which is legal cargo because it lives in `shared` — and
necessary, because a phone number is the customer module's lookup key and it has no other way to learn
one. `CustomerIdentified` gained a `quoteRequestId` for the return leg: without it the answer arrives
with no address on it.

Both listeners are `@TransactionalEventListener` with `@Transactional(propagation = REQUIRES_NEW)`.
The propagation is not decoration: these run after the publishing transaction has committed, so
without one of their own there is nothing to commit — and nothing to commit means the `AFTER_COMMIT`
listener waiting on the far side never fires at all, which silently breaks the second hop only.

## Consequences

`quote_request.customer_id` is written in a different transaction from `phone_verified_at`, a moment
later. Nothing in the flow depends on the gap: §3's submit guard reads `phone_verified_at`, not the
customer, and every screen between verification and the quote is keyed on the request. If something
later needs the two to be simultaneous, this decision is what it has to argue with.

A failure in the customer module leaves a verified phone with no customer row. That is the right
failure of the two available — the alternative is refusing a verification that actually happened, and
telling a customer their correct code was wrong. The repair is to replay `PhoneVerified`, which is why
it is an event and not a method call.

The seam earns its keep here for the first time. `customer` has been a directory of `package-info`
files and one event record since the beginning; this is the first thing to arrive on the other side of
the boundary, and it arrived without either module importing anything of the other's but events.

**Not decided here:** the verified realm. §7 issues a short-lived token after OTP for `/api/quotes/*`,
and none of those routes exists yet (BOYA-56). Designing a token with no consumer would be guessing at
a ticket that has not been written; the anonymous session covers every screen in this flow. The token
arrives with the first route that needs it.
