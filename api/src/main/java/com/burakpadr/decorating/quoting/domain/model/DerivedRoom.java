package com.burakpadr.decorating.quoting.domain.model;

import java.util.List;

/**
 * One area to be captured (§2.1), with the label the customer will read.
 *
 * <p>{@code label} is Turkish. §1's language rule allows it here — {@code room.label} is named as one
 * of the few places customer copy lives — because the label is data the customer sees next to their own
 * photographs, not a UI string the frontend could translate on its own.
 */
public record DerivedRoom(RoomType type, String label, int sortOrder, List<PhotoRole> requiredPhotos) {

	public DerivedRoom {
		requiredPhotos = List.copyOf(requiredPhotos);
	}
}
