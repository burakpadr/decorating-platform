package com.burakpadr.decorating.quoting.domain.model;

/**
 * What a modifier multiplies (§5.7).
 *
 * <p>The split is not cosmetic. A furnished home consumes the same paint and more time, so applying
 * the furnishing surcharge to materials systematically overprices furnished jobs.
 */
public enum ModifierTarget {
	LABOUR,
	MATERIAL,
	BOTH
}
