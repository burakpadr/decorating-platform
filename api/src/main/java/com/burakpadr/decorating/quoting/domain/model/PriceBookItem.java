package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * One priced item of a price book version (§5.11).
 *
 * <p>Costs, not sale prices — margin is applied at step 12, so a figure that already contains margin
 * gets marked up twice. {@code labourMinutes} are PERSON-minutes: §5.8 divides the total by 60 for
 * person-hours and only then by crew size.
 */
public record PriceBookItem(
		ItemCode code,
		PriceUnit unit,
		BigDecimal labourCost,
		BigDecimal materialCost,
		BigDecimal labourMinutes) {}
