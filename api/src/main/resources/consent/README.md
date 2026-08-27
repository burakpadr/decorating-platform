# Consent notices

The text a customer is shown before the photographs are taken (workflow §2.3), one file per version:

```
consent/<lang>/<consent-type>/<version>.md
```

The filename **is** `consent.text_version`. That is the same arrangement as
`prompts/room-analysis/<version>.md` and for the same reason — decision 0006 for the principle, 0018
for why a consent notice belongs here rather than in the frontend's `tr.json`.

**A released version is never edited in place.** `v1.md` is what every row saying `text_version = 'v1'`
refers to, for as long as those rows exist. Correcting a typo in it silently rewrites what those people
agreed to. Add `v2.md` and bump `CURRENT` in `ClasspathConsentNotices` in the same commit.

## The wording is a draft

Like the SMS templates, this copy is **drafted, not final**. Spec §16 puts the privacy notice and the
consent texts with **legal counsel** (BOYA-4), and nothing here has been through them. What the
mechanism guarantees is that whatever text ships can be resolved back from any grant; it does not
guarantee that the text is sufficient.

Two things in `v1.md` are load-bearing and must survive redrafting, because §2.3 asks for exactly them:
what the photographs are used for, and how long they are kept. The retention figure comes from §12 —
photographs are deleted 30 days after the request closes, and the findings drawn from them are kept.

## Language

Turkish only, like `notifications/tr/`. There is no segment budget here — this is a screen, not an SMS —
but the screen has a 15-second budget in §5's inventory, so length is still a constraint.
