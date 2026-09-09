-- What §6's decision needs and had nowhere to read from (BOYA-51, decision 0023).
--
-- §6's fourth threshold is `estimatedTotal > averageJobValue × surveyAmountFactor`.
-- survey_amount_factor has been in price_book since V1 and nothing read it; average_job_value was
-- never there at all, so the branch could not be evaluated by anything.
--
-- A price book coefficient rather than a query over past jobs: it is a business figure like
-- crew_day_cost, it has to be versioned so a decision stays explainable against the version that made
-- it (§4.5), and BOYA-2b is what will set it from historical_job once there are 20-30 rows. Derived
-- live from an empty table it would read 0.00, every job would clear 0 × 2.00, and every reading below
-- 0.80 confidence would go to a survey — the right answer arrived at by accident, which stops being
-- the right answer the day the first row lands.
--
-- 25.000 TL is a PLACEHOLDER and needs the business (§16, BOYA-8), like crew_day_cost and the two VAT
-- rates. It errs low on purpose. Too low sends more jobs to a survey: expensive, and safe. Too high
-- sends an uncertain expensive job straight to a price, which is the failure this threshold exists to
-- prevent. At 25.000 the bar is 50.000 TL, so §5.10's 3+1 clears it and a single room does not.

ALTER TABLE price_book
  ADD COLUMN average_job_value numeric(14,2) NOT NULL DEFAULT 25000.00;

COMMENT ON COLUMN price_book.average_job_value IS
  'PLACEHOLDER (§16, BOYA-8). §6''s survey threshold is average_job_value × survey_amount_factor. Errs low: too low buys surveys, too high skips the one that mattered. BOYA-2b sets it from historical_job.';

-- What §6 decided, and why.
--
-- The decision needs a column of its own because the status does not carry it. §6 reads as though
-- SURVEY were a state, but §3 draws SURVEY_REQUIRED as reachable from PENDING_REVIEW and not from
-- ANALYZING — QuoteRequest.requireSurvey() enforces exactly that — and workflow §4.3 says the survey
-- case "kuyruğa keşif işaretiyle düşer": it lands in the operator's queue *with a survey mark*, and
-- the operator converts it (BOYA-54). So AUTO and SURVEY both leave the request in PENDING_REVIEW and
-- the difference between them is this column. Decision 0023.
--
-- The reasons are needed twice, which is why they are stored rather than re-derived: the operator
-- screen shows the flags at the top (workflow §5.1), and a customer whose survey was triggered by a
-- risk finding is shown a widened range *and the reason* rather than the stage 1 range they already
-- saw (§6). Deriving them in both places would put §6's rule in three.
ALTER TABLE quote_request
  ADD COLUMN review_decision varchar(16),
  ADD COLUMN review_reasons  text[] NOT NULL DEFAULT '{}',
  ADD CONSTRAINT quote_request_review_decision_check
    CHECK (review_decision IS NULL OR review_decision IN ('AUTO', 'RECAPTURE', 'SURVEY'));

COMMENT ON COLUMN quote_request.review_decision IS
  'What §6 decided about the last analysis. Null until one has been reviewed. AUTO and SURVEY both sit in PENDING_REVIEW — the status cannot tell them apart, this column can.';
COMMENT ON COLUMN quote_request.review_reasons IS
  'Why (§6). Turkish: read by the operator on the review screen and, for a survey, by the customer.';
