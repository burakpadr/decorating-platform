package com.burakpadr.decorating.quoting.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * A whole version as the panel shows it (§7, workflow §6).
 *
 * <p>Items come back in §5.6's order rather than the database's, so the list reads the way the price
 * list is discussed: paint first, preparation next, the per-unit extras after, mobilization last.
 *
 * <p>Operator-only, and it says so by carrying figures no customer response may — this is why §1 asks
 * for separate response types rather than one type with fields stripped by a flag.
 *
 * <p>Nothing here is optional: a version always has coefficients and items, and a panel that has to null-check them is a panel that will forget to.
 */
record PriceBookDetailResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String versionCode,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean editable,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Coefficients coefficients,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PriceBookItemResponse> items) {

	/** The figures that are not per item. Nested so the item list stays the readable part. */
	record Coefficients(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal ceilingHeightM,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal grossToNetRatio,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal stage1OpeningRatio,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int crewSize,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal crewHoursPerDay,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal crewDayCost,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal marginRatio,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal marginAlertThreshold,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal labourVatRate,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal materialVatRate,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal baseBandRatio) {}

	static PriceBookDetailResponse of(PriceBookDetail detail) {
		PriceBook book = detail.book();
		List<PriceBookItemResponse> items = Arrays.stream(ItemCode.values())
				.filter(code -> book.items().containsKey(code))
				.map(code -> PriceBookItemResponse.of(book.item(code)))
				.toList();

		return new PriceBookDetailResponse(
				detail.summary().id(),
				detail.summary().versionCode(),
				detail.summary().active(),
				detail.editable(),
				detail.summary().createdAt(),
				new Coefficients(
						book.ceilingHeightM(), book.grossToNetRatio(), book.stage1OpeningRatio(),
						book.crewSize(), book.crewHoursPerDay(), book.crewDayCost(),
						book.marginRatio(), book.marginAlertThreshold(),
						book.labourVatRate(), book.materialVatRate(), book.baseBandRatio()),
				items);
	}
}
