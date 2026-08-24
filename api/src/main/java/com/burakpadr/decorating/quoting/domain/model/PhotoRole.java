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
	DETAIL;

	/**
	 * Whether a room may hold more than one of these (§4.3).
	 *
	 * <p>Only the close-up. The other five name a wall or the ceiling, so a second row for {@code
	 * WALL_1} would be two answers to "what does this wall look like" and the analysis has no way to
	 * choose between them. Close-ups are unlimited because §2.6 invites as many as there are cracks.
	 */
	public boolean isRepeatable() {
		return this == DETAIL;
	}
}
