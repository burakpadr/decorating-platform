package com.burakpadr.decorating.quoting.domain.model;

/**
 * Where a quote is in its own life ({@code quote.status}, §4.6).
 *
 * <p>Not {@link QuoteStatus}, which is the request's state machine (§3). Two different things with
 * one word in the spec: a request moves through ANALYZING and PENDING_REVIEW, and the quote it holds
 * is a document that gets drafted, sent, accepted, expires, or is replaced.
 *
 * <p>{@code DRAFT} is a quote nobody outside has seen. §6's AUTO still means PENDING_REVIEW — there
 * is no automatic sending — so a generated quote sits here until an operator acts (BOYA-54).
 *
 * <p>{@code SUPERSEDED} is what a re-analysis leaves behind. A recapture produces new findings and a
 * new revision; the previous one is not deleted, because a customer may be holding it.
 */
public enum QuoteState {
	DRAFT,
	SENT,
	ACCEPTED,
	EXPIRED,
	SUPERSEDED
}
