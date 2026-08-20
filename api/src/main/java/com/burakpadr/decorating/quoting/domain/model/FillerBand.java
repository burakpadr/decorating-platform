package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * How much of a surface needs filling, as the model reports it (§4.4), with §5.6's conversion to a
 * ratio of the surface area.
 *
 * <p>Bands rather than a number because a vision model cannot see 23% of a wall; it can see "some",
 * "a lot" and "all of it". The ratio lives here so the engine never has to interpret a band.
 */
public enum FillerBand {
	NONE("0.00"),
	LOW("0.15"),
	MEDIUM("0.35"),
	HIGH("0.60"),
	FULL("1.00");

	private final BigDecimal ratio;

	FillerBand(String ratio) {
		this.ratio = new BigDecimal(ratio);
	}

	public BigDecimal ratio() {
		return ratio;
	}
}
