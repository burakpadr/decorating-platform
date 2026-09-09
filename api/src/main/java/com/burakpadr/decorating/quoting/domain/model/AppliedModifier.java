package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One modifier as it actually landed on one line ({@code quote_line_item.applied_modifiers}, §4.6).
 *
 * <p>§4.6 says what this is for: it "answers 'why does this line have a 1.5 factor' six months
 * later". Which means it has to carry the answer, and the answer has two halves.
 *
 * <p>Hence two factors rather than §4.6's single {@code factor}. Its own example is the reason —
 * {@code FURNISHED} is labour-only, because a furnished home takes more time and exactly as much
 * paint, and §5.7 calls that the modifier whose target matters most. One number for it would be wrong
 * on the material half of every furnished line, or wrong on the labour half. Both are stated, and
 * {@code 1.0000} is a modifier that covered this line and did not move this half. Decision 0022.
 *
 * <p>{@code DARK_TO_LIGHT} on walls is why the factor is recorded rather than looked up: it arrives
 * scaled by how much of the wall area was dark (ADR 0014), so the price book's 1.30 is not the number
 * this line was multiplied by, and the price book of six months from now will not be this one anyway.
 */
public record AppliedModifier(ModifierCode code, BigDecimal labourFactor, BigDecimal materialFactor) {

	public AppliedModifier {
		if (code == null || labourFactor == null || materialFactor == null) {
			throw new IllegalArgumentException("an applied modifier states both halves");
		}
		// Four decimals, the scale every ratio in §4.5 is stored at. This is the record of what was
		// applied and not the multiplier itself — the engine multiplies by the unrounded factor and
		// records here — so rounding costs nothing and buys a column somebody can read: 1.0000 beside
		// 1.2500 says "covered this line, moved nothing" at a glance, where 1 does not.
		labourFactor = labourFactor.setScale(4, RoundingMode.HALF_UP);
		materialFactor = materialFactor.setScale(4, RoundingMode.HALF_UP);
	}

	/** Whether it moved anything. A modifier that covered the line and changed neither half is noise. */
	public boolean moved() {
		return labourFactor.compareTo(BigDecimal.ONE) != 0
				|| materialFactor.compareTo(BigDecimal.ONE) != 0;
	}
}
