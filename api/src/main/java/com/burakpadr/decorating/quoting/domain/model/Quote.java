package com.burakpadr.decorating.quoting.domain.model;

import java.util.UUID;

/**
 * A priced quote as it is stored (§4.6).
 *
 * <p>Thin on purpose: the figures are {@link PricedQuote}, which is what the engine returns, and this
 * adds only what makes it a row somebody can find again — which request, which revision, what state,
 * and who produced it. Copying the engine's fields up into this record would be a second place for
 * every total to live.
 *
 * <p>No {@code validUntil} and no {@code sentAt}. §4.6 has both as nullable and they stay null here,
 * because validity runs from the day a quote is <em>sent</em> and not from the day it was computed. A
 * quote generated at 03:00 and approved at 11:00 the next morning would otherwise arrive with a day
 * of its life already spent.
 *
 * <p>The price book version is the engine's, read off {@link PricedQuote#priceBookVersion()} rather
 * than passed alongside: §4.5's promise is that a figure stays explainable against the version that
 * produced it, and two fields could disagree about which one that was.
 */
public record Quote(
		UUID id,
		UUID quoteRequestId,
		int revision,
		QuoteState state,
		QuoteAuthor createdBy,
		PricedQuote priced) {

	public Quote {
		if (id == null || quoteRequestId == null || state == null || createdBy == null
				|| priced == null) {
			throw new IllegalArgumentException("a quote is a request, a state, an author and figures");
		}
		if (revision < 1) {
			throw new IllegalArgumentException("revisions start at 1: " + revision);
		}
	}

	/** The first quote of a request, straight from the engine. */
	public static Quote drafted(UUID id, UUID quoteRequestId, int revision, PricedQuote priced) {
		return new Quote(id, quoteRequestId, revision, QuoteState.DRAFT, QuoteAuthor.SYSTEM, priced);
	}

	public String priceBookVersion() {
		return priced.priceBookVersion();
	}
}
