package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.PriceBookCoefficients;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The version's figures that are not item costs (BOYA-20a).
 *
 * <p>All ten together rather than a patch of whichever moved. Two of them — crew size and crew day
 * cost — are halves of one figure, the cost of a person for a day, and sending one without the other
 * silently rewrites every item's labour (ADR 0016). Once the request has to carry both, carrying the
 * rest costs nothing and the panel never has to reason about which subset it is holding.
 *
 * <p>The bounds are the domain record's, not annotations here: they belong with the figures they
 * describe, and a second copy on the DTO is a second place for them to drift. What this type checks
 * is only that nothing is missing — a null coefficient would otherwise read as a deliberate zero.
 */
record UpdateCoefficientsRequest(
		@NotNull BigDecimal ceilingHeightM,
		@NotNull BigDecimal grossToNetRatio,
		@NotNull BigDecimal stage1OpeningRatio,
		@NotNull Integer crewSize,
		@NotNull BigDecimal crewHoursPerDay,
		@NotNull BigDecimal crewDayCost,
		@NotNull BigDecimal marginRatio,
		@NotNull BigDecimal marginAlertThreshold,
		@NotNull BigDecimal labourVatRate,
		@NotNull BigDecimal materialVatRate) {

	PriceBookCoefficients toDomain() {
		return new PriceBookCoefficients(ceilingHeightM, grossToNetRatio, stage1OpeningRatio, crewSize,
				crewHoursPerDay, crewDayCost, marginRatio, marginAlertThreshold, labourVatRate,
				materialVatRate);
	}
}
