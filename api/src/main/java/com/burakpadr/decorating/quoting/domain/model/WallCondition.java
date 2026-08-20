package com.burakpadr.decorating.quoting.domain.model;

/**
 * Wall condition as the customer declared it in stage 1 (§5.6).
 *
 * <p>{@code UNSURE} is not a fifth degree of damage — it is an admission of not knowing, and §5.9
 * turns it into a wider band rather than a higher price.
 */
public enum WallCondition {
	GOOD,
	MINOR,
	MAJOR,
	UNSURE
}
