package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.DerivedRoom;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One of the areas the layout implied (§2.1), with the label the customer would read.
 *
 * <p>Included in a price calculation because the room list is the assumption most worth disagreeing
 * with: "3+1" is four rooms to the person typing and seven areas to the engine, and a figure that
 * priced a hallway nobody is painting should be visibly wrong rather than quietly high.
 */
record CalculatedRoomResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) RoomType type,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int requiredPhotos) {

	static CalculatedRoomResponse of(DerivedRoom room) {
		return new CalculatedRoomResponse(room.type(), room.label(), room.requiredPhotos().size());
	}
}
