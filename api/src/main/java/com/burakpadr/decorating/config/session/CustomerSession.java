package com.burakpadr.decorating.config.session;

import java.util.UUID;

/**
 * The quote request this browser's cookie was issued for (§7).
 *
 * <p>{@link OwnedQuoteRequest}'s sibling, for the routes that do not name a request in the path.
 * §7 puts the photo routes under {@code /api/photos/{id}}, so there is no id for the resolver to
 * compare the cookie against — the id in the path is a photograph, and which request it belongs to is
 * a fact about a row two joins away.
 *
 * <p>So this type carries no claim about the thing being touched, only about the caller: whoever holds
 * this is the customer working on <em>that</em> request. Checking the photograph against it is the
 * service's job, and it is the only reason the service takes the request id at all.
 */
public record CustomerSession(UUID quoteRequestId) {

	public CustomerSession {
		if (quoteRequestId == null) {
			throw new IllegalArgumentException("a session names a quote request");
		}
	}
}
