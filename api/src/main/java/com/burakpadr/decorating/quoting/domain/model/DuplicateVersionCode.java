package com.burakpadr.decorating.quoting.domain.model;

/**
 * That version code is taken.
 *
 * <p>Caught before the insert rather than left to the unique constraint, because the operator needs
 * to be told which code collided — and because a constraint violation surfacing as a 500 teaches
 * nobody anything.
 */
public class DuplicateVersionCode extends RuntimeException {

	public DuplicateVersionCode(String versionCode) {
		super("a price book version already uses the code " + versionCode);
	}
}
