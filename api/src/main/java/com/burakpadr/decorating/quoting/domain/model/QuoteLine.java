package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * One priced line, after modifiers and before margin (§5.2 steps 5–9).
 *
 * <p>The unit travels with the line because the quantity means nothing without it — 7 rooms of masking
 * and 7 m² of masking are different jobs — and the line is where somebody reads the quantity.
 *
 * <p>{@code labourMinutes} is §4.6's column and the engine was already computing it here — the minutes
 * this line contributes, carrying the same labour modifiers its money does (§5.8: more coats is more
 * time). Kept per line because the quote's day count comes out of the sum, and "which line makes this
 * a three-day job" is the first question anybody asks of a total they doubt.
 *
 * <p>{@code appliedModifiers} is §4.6's audit column: which modifiers touched this line and by how
 * much, per half. Only the ones that moved something are listed — a modifier that covered the line and
 * changed neither half is noise in the one place somebody goes looking for a reason.
 *
 * <p>Labour and material stay separate all the way through because VAT rates differ (§5.8) and
 * because the operator's review needs to see which half a surprise came from. {@code lineTotal} is
 * the only rounded figure here: §5.8 rounds at line total and grand total, never in between.
 */
public record QuoteLine(
		ItemCode code,
		PriceUnit unit,
		BigDecimal quantity,
		BigDecimal labourCost,
		BigDecimal materialCost,
		BigDecimal lineTotal,
		BigDecimal labourMinutes,
		List<AppliedModifier> appliedModifiers) {

	public QuoteLine {
		appliedModifiers = List.copyOf(appliedModifiers);
	}
}
