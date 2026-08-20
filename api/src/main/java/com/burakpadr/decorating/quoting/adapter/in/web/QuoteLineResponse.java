package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceUnit;
import com.burakpadr.decorating.quoting.domain.model.QuoteLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * One line of a calculated quote: how much of what, at what cost.
 *
 * <p>The quantity is what makes the answer checkable — "221 m² of wall" is a claim the business can
 * agree or disagree with, where a total alone is only a number.
 */
record QuoteLineResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemCode code,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PriceUnit unit,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal quantity,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal labourCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal materialCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal lineTotal) {

	static QuoteLineResponse of(QuoteLine line) {
		return new QuoteLineResponse(line.code(), line.unit(),
				line.quantity().setScale(2, java.math.RoundingMode.HALF_UP),
				line.labourCost().setScale(2, java.math.RoundingMode.HALF_UP),
				line.materialCost().setScale(2, java.math.RoundingMode.HALF_UP),
				line.lineTotal());
	}
}
