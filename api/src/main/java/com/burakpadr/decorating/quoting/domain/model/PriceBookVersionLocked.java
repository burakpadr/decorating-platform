package com.burakpadr.decorating.quoting.domain.model;

/**
 * That version can no longer be edited.
 *
 * <p>A version is editable only while nothing has been priced with it: not live, and not pointed at by
 * any quote. Being switched off is not the same as never having priced anything — a customer holding a
 * quote from three weeks ago is holding figures from a version that is no longer active, and those
 * figures have to still be there when they call ({@code docs/decisions/0010}).
 *
 * <p>The way to change a version that has priced something is to copy it, edit the copy and activate
 * that. The panel offers exactly those steps.
 */
public class PriceBookVersionLocked extends RuntimeException {

	public PriceBookVersionLocked(String versionCode) {
		super("price book version " + versionCode
				+ " has priced quotes or is live; copy it and edit the copy instead");
	}
}
