package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-room-type coefficients (§5.3).
 *
 * <p>{@code perimeterFactor} varies by type because the square-room assumption (4.0) badly
 * underestimates elongated spaces: a 1.2 × 6.5 m hallway sits ~40% above it. The 4.1 used for rooms
 * derives from a typical 1.4:1 rectangle, not a square.
 *
 * <p>{@code requiredPhotos} rides along with the pricing coefficients because it belongs to the same
 * question — how much of this kind of room is paintable, and therefore how much of it has to be seen.
 * It is versioned for the same reason: a room list derived last month must stay explainable.
 */
public record RoomTypeConfig(
		RoomType roomType,
		BigDecimal areaWeight,
		BigDecimal perimeterFactor,
		BigDecimal paintableRatio,
		List<PhotoRole> requiredPhotos) {

	public RoomTypeConfig {
		requiredPhotos = List.copyOf(requiredPhotos);
	}
}
