package com.burakpadr.decorating.quoting.domain.model;

/** No quote request with that id. */
public class QuoteRequestNotFound extends RuntimeException {

	public QuoteRequestNotFound(String id) {
		super("no quote request " + id);
	}
}
