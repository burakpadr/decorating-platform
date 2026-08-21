package com.burakpadr.decorating.config.session;

/**
 * The cookie is valid and belongs to a different quote request.
 *
 * <p>Separate from {@link SessionRequired} because the answers differ: a missing session is something
 * the caller can fix by starting again, and this one is not. What the caller is told is deliberately
 * the same either way — 403 with no detail about whose request it was.
 */
public class NotYourQuoteRequest extends RuntimeException {

	public NotYourQuoteRequest(String message) {
		super(message);
	}
}
