package com.burakpadr.decorating.quoting.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Which price book version a request was priced with (§4.2's {@code price_book_id}).
 *
 * <p>§4.5's promise in a read: a figure the customer was shown stays explainable after the next
 * increase, which only works if everything downstream asks this rather than asking what is live now.
 */
public interface PricedWithVersion {

	/** Named for what it answers: two ports on one row both called find() cannot both be implemented. */
	Optional<UUID> pricedWith(UUID quoteRequestId);
}
