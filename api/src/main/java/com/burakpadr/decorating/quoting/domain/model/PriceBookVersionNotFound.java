package com.burakpadr.decorating.quoting.domain.model;

/** No version with that id. Distinct from a bad request: the caller asked for something real-shaped. */
public class PriceBookVersionNotFound extends RuntimeException {

	public PriceBookVersionNotFound(String id) {
		super("no price book version with id " + id);
	}
}
