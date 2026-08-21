package com.burakpadr.decorating.quoting.domain.model;

/**
 * Where a quote request has got to (§3).
 *
 * <p>The names are the database's: {@code quote_request.status} is a varchar with a CHECK constraint
 * listing exactly these nine, per §4's convention that enums are varchar and never a native PG type.
 *
 * <p>{@code CANCELLED} and {@code EXPIRED} are deliberately absent. §3 draws them as terminal
 * transitions from any state, but what they produce is a CLOSED request with an outcome — see
 * {@link CloseOutcome}. Making them statuses would give the business two ways to ask "how many are
 * closed" and two different answers.
 */
public enum QuoteStatus {
	DRAFT,
	PHOTOS_PENDING,
	ANALYZING,
	RECAPTURE_REQUIRED,
	PENDING_REVIEW,
	SURVEY_REQUIRED,
	QUOTE_SENT,
	AWAITING_CONTACT,
	CLOSED
}
