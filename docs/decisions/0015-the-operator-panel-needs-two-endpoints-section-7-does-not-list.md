# 15. The operator panel needs two endpoints §7 does not list

Date: 2026-08-20
Status: accepted

## Context

§7 lists the operator API, and for price books it lists four calls: list versions, create a version
("clone + edit"), bulk increase, activate. BOYA-21 asks the panel to show versions with their age,
**edit items**, apply a bulk increase and activate.

The editing half has no endpoint. "Clone + edit" names it in a parenthesis and stops there, and there
is also no way to fetch one version's figures — the list call returns codes and flags, not costs. A
panel cannot show a price list it cannot read, and cannot edit a copy it cannot write to.

## Decision

Two additions, both under `/api/op/**`:

- `GET /api/op/price-books/{id}` — one version: its coefficients, its items with units and minutes,
  and whether it is still editable.
- `PUT /api/op/price-books/{id}/items/{code}` — the three figures of one item.

**A version is editable only while nothing has been priced with it**: not active, and not referenced by
any quote. That is asked of the database in one query rather than tracked as a flag, because the answer
is a fact about the `quote` table and a flag would be a second copy of it that could disagree.

Being switched off is not enough on its own. A customer holding a three-week-old quote is holding
figures from a version that is no longer active, and those figures have to still be there when they
call (ADR 0010). So a superseded version is as locked as the live one.

`editable` travels in the detail response rather than being inferred by the client: the panel has to
decide what to offer before the operator taps anything. Learning that a version is frozen from a 409
after typing a figure is a worse way to find out.

**The whole item, not a patch.** `PUT` takes labour cost, material cost and duration together. A wrong
figure is usually wrong in all three columns, and a partial update makes it possible to raise a price
while leaving the duration behind — which surfaces months later as a margin nobody can explain.
`labourMinutes` must be above zero: zero drops the item out of the duration and the minimum (§5.8)
while every price still looks right.

## Consequences

The panel's flow is the one the domain already enforces everywhere else: copy, edit the copy, activate.
There is still no endpoint that changes a version anything has been priced with, and the 409 says what
to do instead.

§7's list should gain these two. Until it does, this record is the reason they exist — and the reason
the item endpoint is not the general-purpose "save a price book" that would make the immutability rule
unenforceable.
