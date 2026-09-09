package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.Quote;
import java.util.UUID;

/**
 * {@code quote} and its {@code quote_line_item} rows (§4.6).
 *
 * <p>No read. §4.6's columns are what a screen needs and {@code PricedQuote} is the engine's output
 * type: rehydrating one would mean inventing the figures the row does not keep — the minutes total,
 * the band ratio, the two portions — and a zero in any of them is a lie in a type whose whole job is
 * to be the truth about a price. The operator queue (BOYA-53) reads the columns it needs.
 *
 * <p>{@link #save} takes the revision the caller decided and supersedes whatever came before it, in
 * one transaction. Two writes would leave a request holding two live quotes, and
 * {@code UNIQUE (quote_request_id, revision)} would let the second attempt fail on a number the first
 * one had already taken.
 */
public interface QuoteRepository {

	/** Writes the quote and its lines, marking any earlier revision of the same request superseded. */
	void save(Quote quote);

	/** The revision to give the next quote of this request. 1 when it has none. */
	int nextRevision(UUID quoteRequestId);
}
