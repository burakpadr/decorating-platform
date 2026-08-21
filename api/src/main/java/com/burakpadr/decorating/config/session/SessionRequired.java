package com.burakpadr.decorating.config.session;

/** No usable session cookie on a request that is scoped to one quote request (§7). */
public class SessionRequired extends RuntimeException {

	public SessionRequired(String message) {
		super(message);
	}
}
