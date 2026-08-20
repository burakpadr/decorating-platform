package com.burakpadr.decorating.quoting.domain.model;

/**
 * The modifiers of §5.7. {@code DISTRICT} is absent on purpose: it is not a row the engine looks up
 * by code but a per-district factor applied to the whole subtotal at step 10.
 */
public enum ModifierCode {
	FURNISHED,
	DARK_TO_LIGHT,
	NO_ELEVATOR,
	RUSH
}
