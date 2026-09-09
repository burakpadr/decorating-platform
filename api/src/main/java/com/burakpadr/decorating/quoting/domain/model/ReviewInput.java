package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything §6's decision reads, gathered before it is asked (workflow §4.3).
 *
 * <p>A record rather than six parameters because the evaluator has to stay pure: it is the gate
 * between a home priced from photographs and a home somebody drives to, so it must be drivable from a
 * test with no database and no Spring — the same requirement, for the same reason, as
 * {@code PricingEngine}.
 *
 * <p>{@code skimArea} and {@code wallArea} come off the quote rather than being recounted: §6's
 * fourth risk finding is a ratio of the two, and the engine already produced both — the
 * {@code SKIM_COAT} line's quantity and {@code totalWallSqm}. Recounting them from the findings would
 * be §5.5's deductions implemented twice, and the two copies would answer differently the first time
 * one was corrected.
 *
 * <p>{@code roomsMissingFrames} carries labels and not ids, because the reason is read by people: an
 * operator on the review screen and, when a survey is triggered, the customer.
 */
public record ReviewInput(
		List<RoomAnalysis> analyses,
		BigDecimal estimatedTotal,
		BigDecimal averageJobValue,
		BigDecimal surveyAmountFactor,
		BigDecimal skimArea,
		BigDecimal wallArea,
		int recaptureCount,
		List<String> roomsMissingFrames) {

	public ReviewInput {
		analyses = List.copyOf(analyses);
		roomsMissingFrames = List.copyOf(roomsMissingFrames);
		if (analyses.isEmpty()) {
			throw new IllegalArgumentException("there is nothing to review: no room was analysed");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Named fields at every call site: eight positional arguments of which five are decimals. */
	public static final class Builder {

		private List<RoomAnalysis> analyses = List.of();
		private BigDecimal estimatedTotal = BigDecimal.ZERO;
		private BigDecimal averageJobValue = BigDecimal.ZERO;
		private BigDecimal surveyAmountFactor = BigDecimal.ZERO;
		private BigDecimal skimArea = BigDecimal.ZERO;
		private BigDecimal wallArea = BigDecimal.ZERO;
		private int recaptureCount;
		private List<String> roomsMissingFrames = List.of();

		public Builder analyses(List<RoomAnalysis> analyses) {
			this.analyses = analyses;
			return this;
		}

		public Builder estimatedTotal(BigDecimal total) {
			this.estimatedTotal = total;
			return this;
		}

		public Builder estimatedTotal(String total) {
			return estimatedTotal(new BigDecimal(total));
		}

		public Builder averageJobValue(BigDecimal value) {
			this.averageJobValue = value;
			return this;
		}

		public Builder surveyAmountFactor(BigDecimal factor) {
			this.surveyAmountFactor = factor;
			return this;
		}

		public Builder skim(BigDecimal skimArea, BigDecimal wallArea) {
			this.skimArea = skimArea;
			this.wallArea = wallArea;
			return this;
		}

		public Builder skim(String skimArea, String wallArea) {
			return skim(new BigDecimal(skimArea), new BigDecimal(wallArea));
		}

		public Builder recaptureCount(int count) {
			this.recaptureCount = count;
			return this;
		}

		public Builder roomsMissingFrames(List<String> labels) {
			this.roomsMissingFrames = labels;
			return this;
		}

		public ReviewInput build() {
			return new ReviewInput(analyses, estimatedTotal, averageJobValue, surveyAmountFactor,
					skimArea, wallArea, recaptureCount, roomsMissingFrames);
		}
	}
}
