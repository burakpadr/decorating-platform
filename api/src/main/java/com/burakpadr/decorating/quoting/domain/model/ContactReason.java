package com.burakpadr.decorating.quoting.domain.model;

/**
 * Why the customer is on the call-back list (§3's {@code AWAITING_CONTACT}).
 *
 * <p>Recorded rather than inferred from the quote: an accepted quote and a question about one land in
 * the same state and are two different conversations, and the operator ringing the number needs to know
 * which before it picks up.
 */
public enum ContactReason {
	ACCEPTED,
	SURVEY,
	QUESTION
}
