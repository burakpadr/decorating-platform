package com.burakpadr.decorating.quoting.domain.model;

/**
 * How a quote request ended (§3, {@code quote_request.close_outcome}).
 *
 * <p>{@code WON} and {@code LOST} are the operator's answer after the call. {@code EXPIRED} is the
 * scheduler's, {@code CANCELLED} the operator's from anywhere. The four are kept apart because the
 * conversion rate the business will ask for is WON over (WON + LOST) — counting an expired request as a
 * loss would make an unanswered phone look like a rejected price.
 */
public enum CloseOutcome {
	WON,
	LOST,
	EXPIRED,
	CANCELLED
}
