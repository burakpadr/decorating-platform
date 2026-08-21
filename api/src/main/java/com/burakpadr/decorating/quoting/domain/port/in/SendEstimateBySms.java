package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.shared.PhoneNumber;
import java.util.UUID;

/**
 * Sending the stage 1 range to a phone (§1.5's third option).
 *
 * <p>§1.5 is blunt about why this exists: the result screen is the biggest loss point in the process,
 * and a customer who sees a range and leaves has left no number, so there is no second conversation.
 * The option looks small and is not.
 *
 * @throws com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound if there is no such request
 * @throws IllegalStateException if no range has been computed yet — the message is the range
 */
public interface SendEstimateBySms {

	void send(UUID quoteRequestId, PhoneNumber to);
}
