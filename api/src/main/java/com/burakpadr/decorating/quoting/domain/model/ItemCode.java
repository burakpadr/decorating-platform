package com.burakpadr.decorating.quoting.domain.model;

/**
 * The item codes the engine looks up a quantity for (§5.6).
 *
 * <p>An enum rather than a string because §5.6 maps each code to a specific quantity source: a code
 * the engine does not know is a code it cannot price. A price book may carry rows beyond these — the
 * business's ledger contains work this system does not quote — but those never reach the engine.
 */
public enum ItemCode {
	WALL_PAINT,
	CEILING_PAINT,
	PATCH_FILLING,
	SKIM_COAT,
	PRIMER,
	STAIN_BLOCK_PRIMER,
	WALLPAPER_STRIPPING,
	DOOR_PAINT,
	TRIM_PAINT,
	RADIATOR_PAINT,
	DOWNLIGHT_CUTTING,
	CORNICE_CUTTING,
	MASKING,
	MOBILIZATION
}
