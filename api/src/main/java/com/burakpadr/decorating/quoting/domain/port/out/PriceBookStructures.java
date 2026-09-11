package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.PriceBookStructure;

/**
 * Where the shape of a new price book comes from (BOYA-70).
 *
 * <p>A port because the structure is a versioned resource rather than a table, and the use case
 * should no more know that than it knows where the consent notice lives.
 */
public interface PriceBookStructures {

	/** The structure a new installation is built from. */
	PriceBookStructure current();
}
