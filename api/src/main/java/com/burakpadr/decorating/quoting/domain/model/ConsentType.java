package com.burakpadr.decorating.quoting.domain.model;

/**
 * What a consent row is a consent to (§4.7's {@code consent_type_check}).
 *
 * <p>Mirrors the CHECK constraint rather than a subset of it, because the column is the authority on
 * which values a row may hold and a Java enum that quietly knew fewer would turn a database refusal
 * into a surprise. Only {@link #PROCESSING} is collected today: workflow §2.3 asks for one tick, and
 * §5's screen inventory budgets fifteen seconds for the screen that carries it.
 *
 * <p>{@link #RETENTION_FOR_IMPROVEMENT} is the optional grant — keeping the material to improve the
 * system past the point §12 would otherwise delete it. It has no screen yet and, more to the point, no
 * text: §16 puts the wording with legal counsel (BOYA-4).
 */
public enum ConsentType {

	/** Processing the photographs to produce a quote. Required — there is no quote without it. */
	PROCESSING,

	/** Keeping the material to improve the system. Optional, and not asked for yet. */
	RETENTION_FOR_IMPROVEMENT
}
