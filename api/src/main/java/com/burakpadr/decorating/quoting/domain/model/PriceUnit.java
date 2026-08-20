package com.burakpadr.decorating.quoting.domain.model;

/**
 * What an item's quantity is counted in ({@code price_book_item.unit}, §5.11).
 *
 * <p>The engine never reads this — §5.6 already knows that wall paint is per m² and a door is per
 * door. It exists for the operator: 308 TL is a very different figure per m² than it is per room, and
 * a price list that does not say which is a price list nobody can check.
 */
public enum PriceUnit {
	SQM,
	UNIT,
	ROOM,
	LUMP_SUM
}
