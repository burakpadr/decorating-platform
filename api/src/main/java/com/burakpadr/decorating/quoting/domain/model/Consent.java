package com.burakpadr.decorating.quoting.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One decision a customer made about their data (§4.7, workflow §2.3).
 *
 * <p>A decision, not a permission: {@code granted} is false for a refusal and the row is written all
 * the same. §12 keeps the record so that "said no" and "never reached the screen" stay two different
 * facts — the first is an answer, the second is a funnel problem, and a missing row would read as
 * either.
 *
 * <p>{@code textVersion} is stamped from the notice the customer actually read, never from anything a
 * client sends. {@code ip_address} is left null: nothing in this codebase reads a client address yet,
 * the app sits behind Caddy so the honest value needs a decision about {@code X-Forwarded-For}, and a
 * column filled with the proxy's own address would be worse than an empty one.
 */
public record Consent(UUID id, UUID quoteRequestId, ConsentType type, boolean granted,
		String textVersion, Instant recordedAt) {

	public Consent {
		if (id == null || quoteRequestId == null) {
			throw new IllegalArgumentException("a consent belongs to a request and has an identity");
		}
		if (type == null) {
			throw new IllegalArgumentException("a consent is a consent to something");
		}
		if (textVersion == null || textVersion.isBlank()) {
			throw new IllegalArgumentException(
					"a grant with no notice version cannot be resolved back to what was agreed");
		}
	}
}
