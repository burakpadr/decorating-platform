package com.burakpadr.decorating.quoting.domain.model;

/**
 * No such photograph — or none this session is allowed to know about.
 *
 * <p>Deliberately the same answer for both. A caller that could tell "deleted" apart from "not yours"
 * could map the ids that exist by asking for them one at a time.
 */
public class PhotoNotFound extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PhotoNotFound(String id) {
		super("no photo " + id);
	}
}
