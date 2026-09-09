package com.burakpadr.decorating.quoting.domain.model;

/**
 * What §6 decides about an analysis it has just read.
 *
 * <p>{@code AUTO} is the one worth spelling out, because its name invites the wrong reading: it means
 * PENDING_REVIEW. §6 is explicit — "AUTO still means PENDING_REVIEW, it is not auto-send" — and
 * automatic sending is a phase 3 question nobody has answered yet. The only thing AUTO says is that
 * the analysis raised nothing an operator has to be warned about before looking.
 *
 * <p>{@code RECAPTURE} is asked <b>once</b>. A second unusable set goes to the operator instead: a
 * customer asked twice for the same photograph has been told the system cannot read their home, and
 * they are right.
 */
public enum ReviewDecision {
	AUTO,
	RECAPTURE,
	SURVEY
}
