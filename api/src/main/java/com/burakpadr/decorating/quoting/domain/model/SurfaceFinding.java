package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One analysed surface as the model reported it — a {@code surface_finding} row (§4.4, §6).
 *
 * <p>Nearly {@link SurfaceInput} and deliberately not it. {@code SurfaceInput} is §5.1's engine
 * contract and carries only what §5.6 prices; this carries what was observed, which is a longer list.
 * {@code crackLevel} is the difference that matters: nothing in §5.6 prices it, and §5.9 sends a
 * {@code STRUCTURAL} one to a survey. Giving the engine a field it must ignore is how a field ends up
 * quietly used, so the two records stay apart and BOYA-50 maps one onto the other.
 *
 * <p>{@code photoId} is optional: §6's schema asks for it but does not require it, because a finding
 * about the room as a whole may not come from one frame.
 */
public record SurfaceFinding(
		String surfaceId,
		UUID photoId,
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
