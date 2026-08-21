package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The agreed list, as the capture flow will work through it (workflow §2.2).
 *
 * <p>{@code photoCount} is on the response because §2.2 requires it on the screen: the honest version of
 * how long stage 2 takes. A customer told "28 frames, about eight minutes" up front finishes more often
 * than one who discovers it at room five — "ortada bırakılan çekim, baştan söylenmiş uzun listeden
 * kötüdür".
 */
record ConfirmedRoomsResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Room> rooms,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int photoCount) {

	record Room(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) RoomType type,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PhotoRole> requiredPhotos,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean captureComplete) {}

	static ConfirmedRoomsResponse of(ConfirmedRooms rooms) {
		return new ConfirmedRoomsResponse(
				rooms.rooms().stream()
						.map(room -> new Room(room.id(), room.type(), room.label(),
								room.requiredPhotos(), room.captureComplete()))
						.toList(),
				rooms.photoCount());
	}
}
