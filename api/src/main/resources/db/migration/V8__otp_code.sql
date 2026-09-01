-- The OTP code, which §7 names and §4 never gave a table.
--
-- Spec §11 puts three separate limits on this endpoint and one of them cannot live in
-- rate_limit_counter: "failed attempts — exponential backoff, lock after 5" is a fact about one code
-- being guessed, not about how often a phone asked for one. That counter belongs beside the code it
-- protects, and it is the reason this is a table rather than a column on quote_request.
--
-- The code is stored hashed. It is a six-digit secret with a few minutes to live, so an attacker who
-- reaches the database has better things to do — but the row outlives the code by design (a used or
-- expired row is kept, so "no code was ever sent" and "the code was already used" stay different
-- answers), and a plaintext secret that outlives its own usefulness is a secret nobody is watching.
--
-- One live code per quote request, enforced by a partial unique index rather than by deleting the old
-- row. Requesting a second code must invalidate the first — the opposite of resume_token, which is
-- idempotent because the customer usually taps the older SMS. Here the older SMS is the attack.

CREATE TABLE otp_code (
  id                uuid PRIMARY KEY,
  quote_request_id  uuid NOT NULL REFERENCES quote_request(id) ON DELETE CASCADE,
  phone             varchar(20) NOT NULL,
  code_hash         varchar(88) NOT NULL,
  expires_at        timestamptz NOT NULL,
  attempts          integer NOT NULL DEFAULT 0,
  consumed_at       timestamptz,
  superseded_at     timestamptz,
  created_at        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT otp_attempts_not_negative CHECK (attempts >= 0)
);

-- At most one code a customer can still use, per request. Partial, so the history stays.
CREATE UNIQUE INDEX otp_code_live_idx ON otp_code (quote_request_id)
  WHERE consumed_at IS NULL AND superseded_at IS NULL;

-- The lookup on verify: the live code for this request.
CREATE INDEX otp_code_request_idx ON otp_code (quote_request_id, created_at DESC);

COMMENT ON TABLE otp_code IS
  'Aşama 3.1. One live code per quote request; superseded rather than deleted so that "never sent" '
  'and "already used" remain different answers.';

COMMENT ON COLUMN otp_code.code_hash IS
  'Base64 SHA-256 of the code. The code itself is never stored: it lives in the SMS and in the '
  'customer''s memory, and nowhere else, for as long as expires_at allows.';

COMMENT ON COLUMN otp_code.attempts IS
  'Failed verifications against this code. §11 locks it after five, which rate_limit_counter cannot '
  'express because it counts requests per window, not guesses per secret.';
