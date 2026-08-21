package com.burakpadr.decorating.quoting.domain.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * The handoff token (§7's {@code /api/quote-requests/resume/{token}}, §4.2's {@code resume_token}).
 *
 * <p>It exists because the session cookie is on the wrong device. A customer who fills in two screens
 * on a laptop and asks for the range by SMS opens that link on a phone, which has never seen the
 * cookie — so a link built from the draft id alone answers 403. The token is what the phone exchanges
 * for a session of its own.
 *
 * <p>{@code issueFor} is idempotent. A second SMS must not invalidate the link in the first one: the
 * customer usually taps the older message.
 */
public interface ResumeTokens {

	/** The draft's token, minting one only if it has none or the old one has expired. */
	String issueFor(UUID quoteRequestId);

	/** The draft a token belongs to, or empty for anything unknown, expired or absent. */
	Optional<UUID> resolve(String token);
}
