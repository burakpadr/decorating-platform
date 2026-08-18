# Notification templates

One file per `notification.template_code`, under a directory per language: `tr/QUOTE_READY.txt`.

Eleven templates — seven customer-facing, four operator-facing (§13, workflow §9). All Turkish, all
short. The bodies here are **drafts**: §16 puts the final
wording, the commercial-versus-informational classification and İYS registration with legal
counsel. Structure and placeholders are settled; prose is not.

## Why these are files and not database rows

`notification.template_code` records *which* template was sent. The text itself belongs to the
deployed artifact, so a rollback rolls the wording back with it and a diff shows what changed.

## The character budget is enforced, not documented

`ı İ ğ Ğ ş Ş ç` are outside GSM-7, and one of them drops the whole message to UCS-2: **70 characters
per segment instead of 160**. (`ö ü Ö Ü` and uppercase `Ç` *are* in GSM-7, so the common "avoid
Turkish letters" rule is wrong as well as ugly.)

Billing is per segment, so correctly spelled Turkish under 70 characters costs exactly what
de-accented Turkish under 160 costs. Spell it properly and keep it short.

`SmsSegmentBudgetTest` measures every template **after substituting realistic placeholder values**
— which is what actually goes over the wire — and fails the build when one grows past its budget. It
is also where each budget is justified: `RECAPTURE_NEEDED` is allowed two segments because §6
requires it to name the frame that failed, and that does not fit in 70 characters.

To raise a budget, change it in the test with a comment saying what the extra segment buys.

## Placeholders

`{link}` `{range}` `{date}` `{slot}` `{district}` `{room}` `{estimate}`

Substitution happens in `quoting/adapter/out/notification`. Never interpolate customer-supplied text
into a template.

## Operator templates

`OPERATOR_*` go to the operator by SMS or WhatsApp, not by push — the panel is not kept open, and
push is unreliable on iOS. `OPERATOR_CALLBACK_OVERDUE` is the one that matters most: there is no
calendar in the system, so nothing else reminds anyone that an accepted quote has gone uncalled, and
the workflow document puts it plainly — an acceptance left uncalled for two days is a job that went
to a competitor.

## What must not appear

`QUOTE_READY` carries **no amount** — a bare number without the line-item breakdown gets judged out
of context and the customer never opens the quote. Nothing customer-facing may carry `total_cost` or
`margin_ratio`.
