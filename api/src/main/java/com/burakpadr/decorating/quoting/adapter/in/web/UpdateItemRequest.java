package com.burakpadr.decorating.quoting.adapter.in.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Correcting one line of a draft price list.
 *
 * <p>All three figures every time, rather than a patch of whichever changed: a wrong cost is usually
 * wrong in all three columns, and a partial update makes it possible to raise a price while leaving
 * the duration behind — which shows up months later as a margin nobody can explain.
 *
 * <p>{@code labourMinutes} must be above zero. Zero would drop the item out of the duration and the
 * minimum (§5.8) while every price still looked right.
 */
record UpdateItemRequest(
		@NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal labourCost,
		@NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal materialCost,
		@NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 6, fraction = 2)
		BigDecimal labourMinutes) {}
