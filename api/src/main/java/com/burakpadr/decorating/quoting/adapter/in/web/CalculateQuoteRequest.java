package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Set;

/**
 * A job typed in by hand — the same answers stage 1 asks a customer (§1.1–1.3).
 *
 * <p>Bounded where a slip would be silent: an area of zero would divide the whole quote by nothing, and
 * 5.000 m² is a hotel rather than the flat somebody meant to type. The engine would price both without
 * complaint, which is exactly why the edge refuses them.
 */
record CalculateQuoteRequest(
		@NotNull String districtCode,
		@NotNull @DecimalMin(value = "1.0") @DecimalMax("2000.0") BigDecimal area,
		@NotNull AreaBasis areaBasis,
		@NotNull Layout layout,
		@NotNull QuoteScope scope,
		Set<RoomType> selectedRooms,
		@NotNull WallCondition wallCondition,
		@NotNull Furnishing furnishing,
		@Min(0) @Max(60) int doorCount,
		Boolean doorColourChange,
		Boolean doorCountEstimated,
		Boolean hasElevator,
		Boolean rush) {

	/*
	 * The flags are boxed so an omitted one means "no" rather than rejecting the whole request. A record
	 * with primitive booleans answers a body without "rush" in it with a parse error about null, which
	 * tells the caller nothing about what to send.
	 */

	Set<RoomType> selectedRoomsOrEmpty() {
		return selectedRooms == null ? Set.of() : selectedRooms;
	}

	boolean isDoorColourChange() {
		return Boolean.TRUE.equals(doorColourChange);
	}

	boolean isDoorCountEstimated() {
		return Boolean.TRUE.equals(doorCountEstimated);
	}

	/** Absent means no lift, which is the assumption that costs more — never the cheaper one. */
	boolean isWithElevator() {
		return Boolean.TRUE.equals(hasElevator);
	}

	boolean isRush() {
		return Boolean.TRUE.equals(rush);
	}
}
