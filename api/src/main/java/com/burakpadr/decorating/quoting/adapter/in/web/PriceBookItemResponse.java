package com.burakpadr.decorating.quoting.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBookItem;
import com.burakpadr.decorating.quoting.domain.model.PriceUnit;
import java.math.BigDecimal;

/**
 * One line of the price list, for the operator.
 *
 * <p>The unit is included because the figure means nothing without it — 308 TL per room is not 308 TL
 * per m² — and the minutes because they drive the duration the customer is promised and the minimum
 * that protects a small job, not just the money.
 *
 * <p>The code and the unit are the enums, not their names: the contract then lists the values a client
 * may see, and the generated types make an unknown code a compile error rather than a string that looks
 * fine until it reaches a lookup.
 *
 * <p>All five are always present — an item with no unit or no duration is not a thing this API can return (§5.11).
 */
record PriceBookItemResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ItemCode code,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PriceUnit unit,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal labourCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal materialCost,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal labourMinutes) {

	static PriceBookItemResponse of(PriceBookItem item) {
		return new PriceBookItemResponse(item.code(), item.unit(), item.labourCost(),
				item.materialCost(), item.labourMinutes());
	}
}
