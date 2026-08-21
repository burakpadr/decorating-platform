package com.burakpadr.decorating.quoting.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * The areas the customer agreed to photograph (workflow §2.2, §4.3's {@code room} rows).
 *
 * <p>Not a {@link RoomList}: that is what §2.1 <em>derives</em>, and this is what the customer settled
 * on after adding a second bathroom and taking out the balcony. The two are deliberately different
 * types, because everything downstream — the capture screen, the analysis, the quote — is built against
 * the agreement rather than the derivation.
 *
 * <p>{@code photoCount} is the number §2.2 requires on screen along with the list, and it is the honest
 * version of how long stage 2 takes: eight minutes for a 3+1 is 28 frames, and a customer who is told
 * that up front finishes more often than one who discovers it at room five.
 */
public record ConfirmedRooms(List<ConfirmedRoom> rooms) {

	public ConfirmedRooms {
		rooms = List.copyOf(rooms);
	}

	public int photoCount() {
		return rooms.stream().mapToInt(room -> room.requiredPhotos().size()).sum();
	}

	/**
	 * One agreed area, with the identity its photographs will hang off.
	 *
	 * <p>The label and the required roles are the server's. A label is customer-facing Turkish copy
	 * (§4.3), and the roles decide what the analysis is asked to read — a client that could set either
	 * would be deciding what gets photographed and what it is called.
	 */
	public record ConfirmedRoom(
			UUID id,
			RoomType type,
			String label,
			int sortOrder,
			List<PhotoRole> requiredPhotos,
			boolean captureComplete) {

		public ConfirmedRoom {
			requiredPhotos = List.copyOf(requiredPhotos);
		}
	}
}
