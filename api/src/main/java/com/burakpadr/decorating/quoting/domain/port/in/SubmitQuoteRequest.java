package com.burakpadr.decorating.quoting.domain.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * §7's {@code POST /api/quote-requests/{id}/submit} — the arrow into ANALYZING (workflow §3.2).
 *
 * <p>§3 draws this arrow with two conditions written on it, "all required photos + OTP verified", and
 * until now both lived only in a javadoc: {@code QuoteRequest.submit()} checks the status it is
 * leaving and nothing else. They are checked here, where the facts are — one is a count of photographs
 * and the other a column, and neither belongs inside an aggregate that holds neither.
 *
 * @throws com.burakpadr.decorating.quoting.domain.model.CaptureIncomplete if a required frame is missing
 * @throws com.burakpadr.decorating.quoting.domain.model.PhoneNotVerified if §3.1 has not happened
 */
public interface SubmitQuoteRequest {

	/**
	 * @param respondBy when the customer is told to expect an answer — §8's promise, computed against
	 *     working hours so that a request at 23:00 is not told "within two hours"
	 */
	record Submission(UUID quoteRequestId, Instant respondBy) {}

	Submission submit(UUID quoteRequestId);
}
