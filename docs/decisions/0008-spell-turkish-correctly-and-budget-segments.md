# 8. Spell Turkish correctly, pay for UCS-2, and enforce the segment budget in a test

Date: 2026-08-18
Status: accepted

## Context

Spec §13 warns that Turkish characters double SMS cost: a message containing them drops from GSM-7's
160 characters per segment to UCS-2's 70.

Read as "avoid Turkish characters", that produced a first draft of de-accented templates — *Teklifiniz
hazir. Goruntuleyin.* Customer-facing copy that looks broken.

Two facts make that draft wrong:

- The spec's character list is imprecise. `ö ü Ö Ü` and uppercase `Ç` are inside GSM-7. It is
  `ı İ ğ Ğ ş Ş` and lowercase `ç` that force UCS-2.
- **Billing is per segment.** Correctly spelled Turkish under 70 characters costs exactly what
  de-accented Turkish under 160 costs. The saving was imaginary.

## Decision

Templates are spelled correctly and kept short enough to fit one UCS-2 segment.

The budget is enforced by `SmsSegmentBudgetTest`, not documented in a file. It measures each template
**after** substituting realistic placeholder values, because that is what goes over the wire, and fails
the build when one outgrows its budget.

`RECAPTURE_NEEDED` is budgeted at two segments deliberately: §6 requires it to name the frame that
failed — "the second wall of the living room came out dark", not "retake your photos" — and a room
label plus a link does not fit in 70 characters. It is also the rarest message in the set. Specificity
is worth the segment.

## Consequences

Raising a budget means editing the test and writing down what the extra segment buys. That friction is
the point.

An earlier attempt recorded the budget in a generated `budget.txt`. It was dropped: a text file
describing character counts drifts from the templates it describes, and nothing notices.

The per-segment assumption is the load-bearing one. If a provider is chosen that prices per character
or bundles differently, this record and the template lengths both need revisiting — the SMS provider is
still an open item in §16.
