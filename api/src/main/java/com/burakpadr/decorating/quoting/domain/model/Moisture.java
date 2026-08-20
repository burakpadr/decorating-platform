package com.burakpadr.decorating.quoting.domain.model;

/**
 * Moisture on a surface (§4.4).
 *
 * <p>Anything but {@code NONE} puts stain-block primer on that surface (§5.6). {@code ACTIVE} is
 * also a risk finding for the confidence evaluator — painting over an active leak is a callback, not
 * a job — but that is §6.11's business, not the engine's.
 */
public enum Moisture {
	NONE,
	STAIN,
	ACTIVE;

	public boolean needsStainBlock() {
		return this != NONE;
	}
}
