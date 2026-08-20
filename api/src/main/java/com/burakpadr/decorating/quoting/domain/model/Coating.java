package com.burakpadr.decorating.quoting.domain.model;

/**
 * What a surface is finished with (§4.4 {@code surface_finding.coating}).
 *
 * <p>Only {@code PAINTED} is priced. A tiled kitchen wall is not a cheaper wall — it is not a wall
 * this job touches at all, so §5.5 excludes it entirely rather than discounting it.
 */
public enum Coating {
	PAINTED,
	TILE,
	WOOD,
	BRICK;

	public boolean isPaintable() {
		return this == PAINTED;
	}
}
