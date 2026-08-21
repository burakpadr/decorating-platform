package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.StageOneEstimate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * What stage 1 tells a customer (§1, workflow §1.5).
 *
 * <p>A range, the areas it assumed, and how wide the band is. No cost, no margin, no line items — §1
 * keeps those off customer DTOs, and {@code StageOneEstimateControllerTest} asserts their absence
 * rather than trusting that nobody added them, because the field that leaks is the one somebody adds
 * for debugging.
 *
 * <p>{@code bandRatio} is included so the screen can say why the range is wide. Workflow §1.5 requires
 * that sentence and calls the width a feature: "aralığın geniş olması kusur değil, dürüsttür".
 */
record StageOneEstimateResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal low,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal high,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal bandRatio,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal netArea,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean areaWasGross,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CalculatedRoomResponse> rooms,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int photoCount) {

	static StageOneEstimateResponse of(StageOneEstimate estimate) {
		return new StageOneEstimateResponse(
				estimate.low(),
				estimate.high(),
				estimate.bandRatio(),
				estimate.netArea(),
				estimate.areaWasGross(),
				estimate.rooms().rooms().stream().map(CalculatedRoomResponse::of).toList(),
				// Workflow §2.2 sets the expectation before the customer starts shooting: "3+1" is four
				// rooms to them and seven areas to us, and the number of photos is the honest version of
				// how long stage 2 takes.
				estimate.rooms().photoCount());
	}
}
