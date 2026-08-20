package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * One analysed surface of a room (§4.4 {@code surface_finding}), as the engine reads it.
 *
 * <p>{@code surfaceId} is {@code WALL_1}..{@code WALL_4} for rooms captured with four wall photos and
 * {@code ROOM_GENERAL} for the corner-shot rooms — kitchen, bathroom, hallway. The engine does not
 * care which; it prices the surfaces it is given.
 *
 * <p>Findings, never prices. The model produces observations and this record is where they stop being
 * text and start being arithmetic.
 */
public record SurfaceInput(
		String surfaceId,
		Coating coating,
		Tone tone,
		FillerBand fillerBand,
		boolean skimCoatRequired,
		Moisture moisture,
		boolean wallpaper,
		BigDecimal confidence) {}
