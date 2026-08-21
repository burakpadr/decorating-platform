package com.burakpadr.decorating.config.session;

import java.util.UUID;

/**
 * A quote request the caller has been shown to own (§7).
 *
 * <p>The point of the type is that there is no other way to get one. A handler that declares this
 * parameter has already had its cookie checked against the {@code {id}} in its own path, by
 * {@link OwnedQuoteRequestResolver}, before its first line runs — and a handler that wants the id
 * without the check has to go out of its way to read the path variable as a string.
 *
 * <p>{@code SecurityConfig}'s public chain leaves these paths open at the URL level on purpose:
 * ownership is a fact about one request and one row, not about a path pattern. Somewhere it has to be
 * enforced per request, and a parameter is the one place a developer cannot walk past.
 */
public record OwnedQuoteRequest(UUID id) {

	public OwnedQuoteRequest {
		if (id == null) {
			throw new IllegalArgumentException("an owned request has an id");
		}
	}
}
