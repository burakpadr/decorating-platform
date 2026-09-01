package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.shared.PhoneNumber;
import java.util.UUID;

/**
 * The number of somebody who has not verified anything (§4.2's {@code pending_phone}).
 *
 * <p>Its own port because of what the column is for. §4.1 forbids a {@code customer} row before OTP
 * verification, so this is the only place a stage 1 visitor's number exists — and it is the whole value
 * of §1.5's SMS option, which is the last chance to reach somebody who is about to leave. It moves to
 * {@code customer} on verification and is nulled here.
 */
public interface PendingPhoneWriter {

	void storePendingPhone(UUID quoteRequestId, PhoneNumber phone);

	/**
	 * The number stored against this request, before anybody proved it.
	 *
	 * <p>Read once, at verification, to say whose number was just proved. Empty after that: the schema
	 * moves it to {@code customer} and nulls the column.
	 */
	java.util.Optional<PhoneNumber> pendingPhone(UUID quoteRequestId);
}
