# 18. The consent notice is a backend resource, not frontend copy

Date: 2026-08-27
Status: accepted

## Context

Workflow §2.3 puts a data notice on the capture guidance screen and takes consent for it: "fotoğrafların
ne için kullanılacağı ve ne kadar süre saklanacağı bilgisi burada verilir ve onayı alınır." The
`consent` table has carried `text_version varchar(32) NOT NULL` since `V1__baseline.sql`, with the
column comment "Which version of the notice this grant referred to", and §12 states the rule the column
exists for: *consent is versioned so you know which notice each grant referred to*.

Nothing in the repository held the notice itself. Two homes were plausible and the project's own rules
pointed at different ones.

`web-ui/CLAUDE.md` says: **"Every string the user reads comes from `i18n/locales/tr.json`."** The notice
is read by the customer, so by that rule it is copy like any other. But `tr.json` is not versioned.
Nothing stamps a version on it, nothing stops a wording change from shipping without one, and the moment
that happens every existing row saying `text_version = 'v1'` points at words that are no longer there.
That is the exact failure §12 is written to prevent, and it fails silently — the rows still look fine.

Decision 0006 already settled the shape of the answer for two other kinds of text, and its argument
transfers without modification: a prompt is an input to persisted analysis results the way a price book
version is an input to persisted quotes, so editing one that existing rows reference rewrites history.
A consent notice is an input to persisted `consent` rows in precisely that sense. 0006 did not name it
because at the time no code touched the `consent` table at all.

The alternative considered was keeping the words in `tr.json` and the version constant in Java, held in
step by a test in the manner of `districts.spec.ts` — which does parse the SQL seed and assert the
duplicated list still matches. That works for a district list, where the two copies exist because
prerendering cannot reach the API. Here the duplication would have no such cause, and the guard would
be protecting a legal record rather than a dropdown.

## Decision

The notice lives in the deployed backend artifact, one file per version:

```
api/src/main/resources/consent/<lang>/<consent-type>/<version>.md
```

The filename **is** `consent.text_version`, exactly as `prompts/room-analysis/<version>.md` is
`room_analysis.prompt_version`. `GET /api/consent-notices/{type}` serves the text together with the
version that names it; the client echoes that version back with the decision, and a version the server
no longer publishes is refused (`urn:decorating:consent-notice-changed`) rather than quietly replaced
with the current one.

Which version is current is a constant in `ClasspathConsentNotices`, not a configuration property. A
property could name a version the running artifact does not contain, which is a grant referring to a
text nobody can produce.

**This is the one deliberate exception to "every string the customer reads comes from `tr.json`."** It
is flagged in place, in the same way the PWA manifest and `app/utils/districts.ts` are. Everything else
on that screen — the three shooting rules, the checkbox label, the buttons, every error — is ordinary
copy and stays in `tr.json` under `captureGuide`.

## Consequences

A grant can always be resolved back to the words it was given against, and a rollback of the application
rolls the notice back with it. A diff shows what changed between two versions of a notice, which is what
a reviewer of a privacy text actually wants to see.

The screen cannot render its notice without a request, so it has a loading state and a failure state
that other pages do not need. It renders the Markdown through `app/utils/noticeText.ts` rather than
`v-html`: the text arrives over the wire, and a screen that will put arbitrary markup on the page is one
compromised response away from being a phishing page on our own domain.

The operator cannot edit the notice through the panel. That is the same trade 0006 made and for a
stronger reason here: changing a privacy notice without a deploy is precisely the thing to prevent.

**The wording that ships is a draft.** §16 puts the privacy notice and the consent texts with legal
counsel (BOYA-4), and `v1.md` has not been through them. What this decision guarantees is that whatever
text ships is resolvable from any grant; it does not guarantee the text is sufficient. When counsel
delivers, that is `v2.md` and a bumped constant in the same commit — never an edit to `v1.md`.

`RETENTION_FOR_IMPROVEMENT` is accepted by the table's CHECK constraint and has no notice, so asking for
it answers 404 rather than failing. It is out of scope until §16 writes the words for it.
