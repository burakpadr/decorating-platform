package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A price book with the money taken out (BOYA-70).
 *
 * <p>What setup starts from: §5.3–5.7's engineering — how a home's area is shared between rooms, how
 * much wall a room of that shape has, what each modifier covers, which unit each item is sold in and
 * how long it takes — and not one figure in lira. The installing business supplies those afterwards,
 * because a price that ships with the repository is a price nobody in that business entered
 * (decision 0024).
 *
 * <p>{@code version} travels because a released structure is never edited in place (decision 0006).
 * An install that ran against {@code v1} produced a book somebody has been quoting from.
 */
public record PriceBookStructure(
		String version,
		BigDecimal ceilingHeightM,
		BigDecimal grossToNetRatio,
		BigDecimal stage1OpeningRatio,
		BigDecimal doorOpeningM2,
		BigDecimal windowOpeningM2,
		int crewSize,
		BigDecimal crewHoursPerDay,
		BigDecimal dayRoundingTolerance,
		BigDecimal surveyAmountFactor,
		BigDecimal baseBandRatio,
		Map<ItemCode, StructureItem> items,
		Map<ModifierCode, PriceModifier> modifiers,
		Map<RoomType, RoomTypeConfig> roomTypes) {

	public PriceBookStructure {
		items = Map.copyOf(items);
		modifiers = Map.copyOf(modifiers);
		roomTypes = Map.copyOf(roomTypes);
	}

	/**
	 * An item before it has a price: what it is sold by, and how long it takes.
	 *
	 * <p>No labour cost, because ADR 0016 derives that from the minutes at the version's crew rate,
	 * and no material cost, because that is one of the questions setup asks.
	 */
	public record StructureItem(ItemCode code, PriceUnit unit, BigDecimal labourMinutes) {}
}
