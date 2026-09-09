# 23. A survey is a mark on the queue, not a status

Date: 2026-09-09
Status: accepted

## Context

§6 writes its decision as three outcomes — `AUTO`, `RECAPTURE`, `SURVEY` — and §3 has a
`SURVEY_REQUIRED` state. Read together they suggest that deciding SURVEY sets that status.

They cannot mean that. §3 draws `SURVEY_REQUIRED` as reachable from `PENDING_REVIEW` and not from
`ANALYZING`, and `QuoteRequest.requireSurvey()` has enforced exactly that since BOYA-23. Workflow §4.3
settles it in its own words: the survey case *"kuyruğa keşif işaretiyle düşer"* — it lands in the
operator's queue **with a survey mark**. The operator is the one who converts it (BOYA-54), which is
the same principle as §6's "AUTO still means PENDING_REVIEW, it is not auto-send", seen from the other
end. There is one human gate and neither outcome skips it.

Building the evaluator also turned up three things §6's decision needs and nothing could supply.

## Decision

**`AUTO` and `SURVEY` both leave the request in `PENDING_REVIEW`.** What separates them is
`quote_request.review_decision`, and the reasons sit beside it in `review_reasons` (V11). `RECAPTURE`
is the one outcome that moves the request elsewhere, because it is the one that needs the customer
again.

The decision needs a column because the status cannot carry it: two outcomes, one state. The reasons
need one because two screens read them and neither can recompute them — the operator's queue shows
the flags at the top (workflow §5.1), and a customer sent to a survey by a risk finding is shown a
widened range *and the reason* rather than the stage 1 range they have already seen (§6). Deriving
them in both places would put §6's rule in three.

**`average_job_value` is a price book coefficient** (V11). §6's fourth threshold is
`estimatedTotal > averageJobValue × surveyAmountFactor`. `survey_amount_factor` has been in
`price_book` since V1 and nothing read it; the average was never there at all, so the branch could not
be evaluated by anything. It belongs in the price book for the reasons `crew_day_cost` does: it is a
business figure, it has to be versioned so a decision stays explainable against the figures that made
it (§4.5), and BOYA-2b will set it from `historical_job`.

Deriving it live from `historical_job` was the alternative and it fails today in a way worth naming:
the table is empty, the average reads 0.00, every job clears `0 × 2.00`, and every reading below 0.80
confidence goes to a survey. That is the right answer arrived at by accident, and it stops being the
right answer the day the first row lands. The seeded 25.000 TL is a placeholder needing the business
(§16, BOYA-8) and it errs low on purpose: too low buys surveys, too high skips the expensive uncertain
job the threshold exists for.

**The confidence §6 thresholds against is the average of the rooms' own figures** — each of which is
decision 0021's average over every plane that room read, ceiling included. An average of averages,
every room counting once: a home is not more confidently read because six of its seven rooms were
easy.

This is **not** the number §5.9 widens the band with, and the difference is worth stating before
somebody compares a band to a decision. The engine computes its own confidence term over
`surface_finding` alone, because `RoomInput` carries no ceiling confidence to give it — deliberately,
since the engine prices the ceiling's findings and not the reading's reliability. Unifying them means
handing the engine a figure it does not price and changing §5.9's arithmetic, with §5.10's fixture
downstream of it. Left apart, and left recorded.

## Consequences

`CeilingFinding.isRisk()` finally has a caller. ADR 0017 wrote it without one on purpose — §5.9's list
says "any *surface* moisture == ACTIVE", a ceiling is not a `surface_finding` row, and for the length
of the project that sentence read straight past the one place a leak is most likely. The evaluator asks
the predicate rather than restating the rule, which is what stops it drifting back.

Everything is priced before it is judged, including readings that turn out to want a recapture. §6's
first line needs no price, so the order could be reversed — but only by asking the unusable-frame
question in the use case and the rest of §6 in the evaluator, which would split one rule to save one
pure computation. The draft that leaves behind is never shown: the request sits in
`RECAPTURE_REQUIRED` and the re-analysis supersedes the quote.

A request whose analysis job ends `FAILED` after §8's three attempts never becomes complete, so it
never gets a quote and stays in `ANALYZING`. That is not a stuck row by accident — the `FAILED` job is
§8's flag and the operator queue is what surfaces it (BOYA-53) — but nothing yet puts a time limit on
how long a customer waits for a promise §3.2 already made them.
