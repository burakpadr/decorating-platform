package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.PricedQuote;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * What the internal tool answers with (workflow §12, increment 1).
 *
 * <p>Operator-only, and it carries the figures §1 keeps away from customers: {@code totalCost} and the
 * margin behind it. That is the point of the screen — the business is comparing the engine's cost with
 * its own, and a response that hid the cost would leave nothing to compare.
 *
 * <p>{@code labour} and {@code material} are the same job one half at a time. The business quotes labour
 * alone — the customer buys the paint — so the screen has to be able to say which price it is showing,
 * and the split arrives computed rather than left to the client to reconstruct.
 *
 * <p>Everything the price rests on comes with it: the version that priced it, the net area used, whether
 * that area was converted, the areas assumed, and the quantity behind every line. A number nobody can
 * take apart is a number nobody will trust.
 */
record QuoteCalculationResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String priceBookVersion,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal netArea,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean areaWasGross,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CalculatedRoomResponse> rooms,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int photoCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuoteLineResponse> lines,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalMinutes,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int billableDays,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal minimumCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean minimumBinding,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal subtotalExVat,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal vatAmount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal total,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuotePortionResponse labour,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuotePortionResponse material,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal bandRatio,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal bandLow,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal bandHigh) {

	static QuoteCalculationResponse of(QuoteCalculation calculation) {
		PricedQuote quote = calculation.quote();
		return new QuoteCalculationResponse(
				calculation.priceBookVersion(),
				calculation.netArea(),
				calculation.areaWasGross(),
				calculation.rooms().rooms().stream().map(CalculatedRoomResponse::of).toList(),
				calculation.rooms().photoCount(),
				quote.lines().stream().map(QuoteLineResponse::of).toList(),
				quote.totalMinutes(),
				quote.billableDays(),
				quote.minimumCost(),
				quote.minimumBinding(),
				quote.totalCost(),
				quote.subtotalExVat(),
				quote.vatAmount(),
				quote.total(),
				QuotePortionResponse.of(quote.labour()),
				QuotePortionResponse.of(quote.material()),
				quote.bandRatio(),
				quote.bandLow(),
				quote.bandHigh());
	}
}
