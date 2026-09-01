package com.burakpadr.decorating.quoting.domain.port.out;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes the three columns a verification settles on {@code quote_request} (§4.1).
 *
 * <p>Separate from {@code PendingPhoneWriter} because it is the other half of that column's life: one
 * stores a number nobody has proved, this one records that it was proved and clears it. The schema's
 * own comment describes the pair — "pre-verification contact (moved to customer on verify, then
 * nulled)".
 */
public interface VerifiedPhoneWriter {

	/** When this request's phone was proved, or empty. §3's submit arrow is guarded on it. */
	java.util.Optional<Instant> verifiedAt(UUID quoteRequestId);

	void recordVerified(UUID quoteRequestId, Instant verifiedAt);

	/**
	 * Attaches the customer the {@code customer} module created, and clears the pending number.
	 *
	 * <p>A second step rather than part of the first: the customer row is made in another module in
	 * answer to an event, so its id does not exist yet when the phone is marked verified.
	 */
	void attachCustomer(UUID quoteRequestId, UUID customerId);
}
