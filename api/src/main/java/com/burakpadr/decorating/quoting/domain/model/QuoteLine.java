package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * One priced line, after modifiers and before margin (§5.2 steps 5–9).
 *
 * <p>Labour and material stay separate all the way through because VAT rates differ (§5.8) and
 * because the operator's review needs to see which half a surprise came from. {@code lineTotal} is
 * the only rounded figure here: §5.8 rounds at line total and grand total, never in between.
 */
public record QuoteLine(
		ItemCode code,
		BigDecimal quantity,
		BigDecimal labourCost,
		BigDecimal materialCost,
		BigDecimal lineTotal) {}
