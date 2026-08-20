package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * A job as the operator types it in (workflow §12, increment 1) — the same answers stage 1 collects
 * from a customer (§1.1–1.3), asked of somebody who already knows what the job costs.
 *
 * <p>It is not {@link PricingInput}. This is the form: an area that may be gross, a layout rather than
 * a room list, and a scope. Turning it into what the engine takes is the use case's job, and keeping
 * the two apart is what stops the panel from having to know the engine's contract.
 */
public record QuoteCalculationCommand(
		String districtCode,
		BigDecimal area,
		AreaBasis areaBasis,
		Layout layout,
		QuoteScope scope,
		Set<RoomType> selectedRooms,
		WallCondition wallCondition,
		Furnishing furnishing,
		int doorCount,
		boolean doorColourChange,
		boolean doorCountEstimated,
		boolean hasElevator,
		boolean rush) {

	public QuoteCalculationCommand {
		selectedRooms = Set.copyOf(selectedRooms);
	}
}
