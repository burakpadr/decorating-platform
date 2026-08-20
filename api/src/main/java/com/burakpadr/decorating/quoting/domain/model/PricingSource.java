package com.burakpadr.decorating.quoting.domain.model;

/**
 * Which stage built the input (§5.1).
 *
 * <p>Stage 1 and stage 2 build the same {@link PricingInput}; the engine must not know which
 * produced it beyond the two places §5.5 and §5.9 name explicitly — the opening deduction and the
 * band.
 */
public enum PricingSource {
	STAGE_1,
	STAGE_2
}
