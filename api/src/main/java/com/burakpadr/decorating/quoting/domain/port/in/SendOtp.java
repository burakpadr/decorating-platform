package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.shared.PhoneNumber;
import java.util.UUID;

/**
 * §7's {@code POST /api/otp/send} (workflow §3.1, BOYA-45).
 *
 * <p>Sending a second code invalidates the first. The opposite of {@code ResumeTokens.issueFor},
 * which is idempotent because the customer usually taps the older SMS — here the older SMS is the
 * attack.
 *
 * @throws com.burakpadr.decorating.quoting.domain.model.TooManyOtpRequests if §11's limits are reached
 * @throws com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound if there is no such request
 */
public interface SendOtp {

	/**
	 * @param scopeKeyForAddress §11's coarse backstop, {@code "ip:1.2.3.4"}. Coarse on purpose: CGNAT
	 *     means an address is thousands of people, and a strict limit there blocks real customers.
	 * @return when the code stops working. Told rather than assumed: the screen counts it down, and a
	 *     client that guessed the lifetime would show a clock that disagreed with the server about when
	 *     the code died — which the customer discovers by typing a code that has just expired.
	 */
	java.time.Instant send(UUID quoteRequestId, PhoneNumber phone, String scopeKeyForAddress);
}
