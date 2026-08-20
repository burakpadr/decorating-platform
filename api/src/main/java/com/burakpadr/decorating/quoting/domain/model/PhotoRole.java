package com.burakpadr.decorating.quoting.domain.model;

/**
 * The frames a room is captured in ({@code photo.role}, workflow §2.4).
 *
 * <p>Kitchens and bathrooms ask for fewer frames than a bedroom on purpose: most of their wall is
 * tile and cupboard, so there is less to paint and less to see.
 *
 * <p>{@code DETAIL} is never required. It is the close-up of a crack or a stain the customer is
 * invited to add after the required frames (§2.6) — unlimited and skippable, and probably the most
 * valuable frame in the set, because a hairline crack does not survive a wide shot.
 */
public enum PhotoRole {
	WALL_1,
	WALL_2,
	WALL_3,
	WALL_4,
	CEILING,
	DETAIL
}
