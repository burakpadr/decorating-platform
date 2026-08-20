package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * Per-room-type coefficients (§5.3).
 *
 * <p>{@code perimeterFactor} varies by type because the square-room assumption (4.0) badly
 * underestimates elongated spaces: a 1.2 × 6.5 m hallway sits ~40% above it. The 4.1 used for rooms
 * derives from a typical 1.4:1 rectangle, not a square.
 */
public record RoomTypeConfig(
		RoomType roomType,
		BigDecimal areaWeight,
		BigDecimal perimeterFactor,
		BigDecimal paintableRatio) {}
