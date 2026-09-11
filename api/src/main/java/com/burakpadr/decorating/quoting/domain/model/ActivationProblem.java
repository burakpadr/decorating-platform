package com.burakpadr.decorating.quoting.domain.model;

/**
 * One reason a price book version may not be made the live one (BOYA-20a).
 *
 * <p>Structured rather than a sentence, because the panel acts on it: a missing item sends the
 * operator to the item list, a labour figure that does not reconcile sends them to the duration.
 * {@code subject} names the item or room type, so nobody has to read fourteen rows to find out which.
 */
public record ActivationProblem(Kind kind, String subject, String detail) {

	public enum Kind {

		/** §5.6 looks this code up for every quote; the engine throws when it is not there. */
		MISSING_ITEM,

		/** §5.3 weighs the home's area by room type, and throws the same way. */
		MISSING_ROOM_TYPE,

		/**
		 * A figure that decides money is still at zero — nobody has entered it. The wizard leaves a
		 * version in exactly this shape between its first screen and its last (BOYA-70), and a book at
		 * zero prices every job at cost with no VAT. Zero reads as unset here for the same reason it
		 * does in BOYA-69's setup status: the columns are NOT NULL, so there is no other way to say it.
		 */
		MONEY_NOT_ENTERED,

		/**
		 * The item states the cost of labour twice — as a TL figure and as minutes at the book's own
		 * crew rate — and the two disagree. ADR 0016: a figure above the crew rate is margin applied
		 * twice, one below it is time nobody bills.
		 */
		LABOUR_DOES_NOT_RECONCILE
	}
}
