package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * One analysed surface as the model reported it — a {@code surface_finding} row (§4.4, §6).
 *
 * <p>Nearly {@link SurfaceInput} and deliberately not it. {@code SurfaceInput} is §5.1's engine
 * contract and carries only what §5.6 prices; this carries what was observed, which is a longer list.
 * {@code crackLevel} is the difference that matters: nothing in §5.6 prices it, and §5.9 sends a
 * {@code STRUCTURAL} one to a survey. Giving the engine a field it must ignore is how a field ends up
 * quietly used, so the two records stay apart and BOYA-50 maps one onto the other.
 *
 * <p>No photograph id. §6's schema had an optional one and nothing could fill it: the model is shown
 * labelled images and never told their ids, {@code surface_finding} has no column for one, and where
 * the field would have meant something — {@code WALL_2} — the label already says which frame it was.
 * On {@code ROOM_GENERAL} it could not mean anything, because that surface is read from several. A
 * field the model has to invent, joined to nothing, is worse than no field (decision 0021).
 */
public record SurfaceFinding(
		String surfaceId,
		Coating coating,
		Tone tone,
		FillerBand fillerBand,
		boolean skimCoatRequired,
		CrackLevel crackLevel,
		Moisture moisture,
		boolean wallpaper,
		BigDecimal confidence) {

	public SurfaceFinding {
		if (surfaceId == null || coating == null || tone == null || fillerBand == null
				|| crackLevel == null || moisture == null || confidence == null) {
			throw new IllegalArgumentException("a surface finding states every observation it was asked "
					+ "for; a missing one is a response that should not have validated");
		}
	}
}
