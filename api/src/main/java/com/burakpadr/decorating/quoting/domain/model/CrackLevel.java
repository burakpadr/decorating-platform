package com.burakpadr.decorating.quoting.domain.model;

/**
 * How badly a surface is cracked (§4.4 {@code surface_finding.crack_level}).
 *
 * <p>Nothing in §5.6 prices this, and that is not an omission: filling a crack is what
 * {@link FillerBand} is for, and the two describe the same repair from different ends — the band says
 * how much of the wall, this says how bad the worst of it is. What the level decides is whether the
 * job is a painting job at all. {@code STRUCTURAL} is a §5.9 risk finding, so it goes to a survey
 * rather than to a price, and the evaluator (BOYA-51) is what asks.
 */
public enum CrackLevel {
	NONE,
	HAIRLINE,
	VISIBLE,
	STRUCTURAL
}
