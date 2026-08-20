package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * The engine's input contract (§5.1).
 *
 * <p>{@code netArea} is always net: where the customer gave gross, the caller applies
 * {@code grossToNetRatio} and sets {@code areaWasGross}, which §5.9 turns into a wider band. The
 * engine does not convert, so it cannot convert twice.
 *
 * <p>{@code doorCountEstimated} is not in §5.1's record but §5.9 prices it (+0.03 to the band), and
 * a band term whose input cannot be expressed is a band term that never fires. Carried here as the
 * narrowest resolution; the two sections disagree and one of them is wrong.
 */
public record PricingInput(
		String districtCode,
		BigDecimal netArea,
		boolean areaWasGross,
		List<RoomInput> rooms,
		Furnishing furnishing,
		int doorCount,
		boolean doorColourChange,
		boolean doorCountEstimated,
		boolean hasElevator,
		boolean rush,
		PricingSource source) {

	public PricingInput {
		rooms = List.copyOf(rooms);
	}
}
