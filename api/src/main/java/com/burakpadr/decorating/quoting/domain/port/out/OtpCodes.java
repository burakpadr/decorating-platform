package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.shared.PhoneNumber;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code otp_code} rows (§4, added by V8).
 *
 * <p>Rows are superseded and consumed, never deleted. "No code was ever sent to this request" and
 * "that code was used an hour ago" are different answers to a customer on the phone, and a table that
 * deleted could not tell them apart.
 */
public interface OtpCodes {

	/** What a verification needs to know about the one code still in play. */
	record LiveCode(UUID id, String codeHash, Instant expiresAt, int attempts) {}

	/**
	 * Retires whatever code this request was holding.
	 *
	 * <p>Called before issuing another, because {@code otp_code_live_idx} allows exactly one — the
	 * schema refuses a second rather than trusting this to remember.
	 */
	void supersedeLive(UUID quoteRequestId, Instant when);

	void issue(UUID id, UUID quoteRequestId, PhoneNumber phone, String codeHash, Instant expiresAt);

	Optional<LiveCode> findLive(UUID quoteRequestId);

	/** @return the attempt count after this guess */
	int recordFailedAttempt(UUID codeId);

	void consume(UUID codeId, Instant when);
}
