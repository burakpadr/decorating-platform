package com.burakpadr.decorating.quoting.domain.model;

import java.util.List;

/**
 * The derived list of areas to photograph (§2.1), in capture order.
 *
 * <p>{@link #photoCount()} is on the list rather than computed by a caller because §2.2 shows the
 * customer that number before they start: an expectation set at the beginning beats a capture
 * abandoned in the middle.
 */
public record RoomList(List<DerivedRoom> rooms) {

	public RoomList {
		rooms = List.copyOf(rooms);
	}

	public int photoCount() {
		return rooms.stream().mapToInt(room -> room.requiredPhotos().size()).sum();
	}

	public int size() {
		return rooms.size();
	}
}
