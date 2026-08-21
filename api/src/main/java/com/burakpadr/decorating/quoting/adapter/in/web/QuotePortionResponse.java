package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.QuotePortion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * One half of the answer — labour, or material — at each of the stages the whole is reported at.
 *
 * <p>Here because the business quotes labour on its own: the customer buys the paint, so "is labour
 * included" is a question about which price is being said out loud. The client could not work this out
 * for itself without re-applying margin and both VAT rates, which is the duplication ADR 0016 exists to
 * describe.
 */
record QuotePortionResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal subtotalExVat,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal vatAmount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal total) {

	static QuotePortionResponse of(QuotePortion portion) {
		return new QuotePortionResponse(
				portion.cost(), portion.subtotalExVat(), portion.vatAmount(), portion.total());
	}
}
