package com.burakpadr.decorating.quoting.domain.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Records what a customer was told (§4.2's {@code estimate_low}, {@code estimate_high},
 * {@code net_area}, {@code price_book_id}).
 *
 * <p>Its own port rather than another {@code save} on {@link QuoteRequestRepository}: these four
 * columns are the output of pricing rather than part of the draft the customer is filling in, and
 * {@code QuoteRequest} does not carry them for the same reason it does not carry a quote. What matters
 * is that {@code price_book_id} is written with the figures — §4.5's promise that a range the customer
 * saw stays explainable after the next increase depends on this row remembering which version produced
 * it.
 *
 * <p>The version arrives as its code rather than its id: the domain's {@code PriceBook} has no id and
 * should not grow one to satisfy a column. Resolving the code is the adapter's job, and
 * {@code version_code} is unique, so the answer is exact.
 */
public interface StageOneEstimateWriter {

	void recordEstimate(UUID quoteRequestId, BigDecimal netArea, String priceBookVersion,
			BigDecimal low, BigDecimal high);
}
