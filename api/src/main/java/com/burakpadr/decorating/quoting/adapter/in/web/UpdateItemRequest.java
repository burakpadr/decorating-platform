package com.burakpadr.decorating.quoting.adapter.in.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Correcting one line of a draft price list.
 *
 * <p>Two figures, not three. Labour cost is derived from the duration at the version's crew rate
 * (ADR 0016), so there is nothing here to send it: an item's TL figure and its minutes are one
 * statement, and letting the panel set them independently is what produced a price book billing
 * 33,321 TL of labour for a crew costing 13,500. What an operator corrects is how long the work takes
 * and what it costs in materials. Raising the price of labour is a change to {@code crew_day_cost},
 * which is a coefficient of the whole version.
 *
 * <p>Both figures every time, rather than a patch of whichever changed: a wrong item is usually wrong
 * in both, and a partial update makes it possible to correct the paint and leave the duration behind.
 *
 * <p>{@code labourMinutes} must be above zero. Zero would drop the item out of the duration and the
 * minimum (§5.8) and, since labour follows the minutes, price the work at nothing.
 */
record UpdateItemRequest(
		@NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal materialCost,
		@NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 6, fraction = 2)
		BigDecimal labourMinutes) {}
