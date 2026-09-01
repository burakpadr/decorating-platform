package com.burakpadr.decorating.quoting.domain.port.in;

import java.util.UUID;

/**
 * §7's {@code POST /api/otp/verify} (workflow §3.1, BOYA-45).
 *
 * <p>On success the request carries a verified phone, and a {@code customer} row exists for it — §4.1
 * is explicit that one appears at this moment and not before. The row is created by the
 * {@code customer} module, which learns about this through an event; nothing here reaches across the
 * boundary to make it (decision 0019).
 *
 * @throws com.burakpadr.decorating.quoting.domain.model.OtpRefused if the code does not verify
 * @throws com.burakpadr.decorating.quoting.domain.model.OtpLocked after §11's fifth wrong guess
 */
public interface VerifyOtp {

	void verify(UUID quoteRequestId, String code);
}
