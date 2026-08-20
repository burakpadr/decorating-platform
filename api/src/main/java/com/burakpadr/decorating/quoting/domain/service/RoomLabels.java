package com.burakpadr.decorating.quoting.domain.service;

import com.burakpadr.decorating.quoting.domain.model.RoomType;

/**
 * The Turkish name of each kind of area, as the customer reads it beside their own photographs.
 *
 * <p>§1's language rule keeps everything English except a few named places, and {@code room.label} is
 * one of them: the label is persisted data the customer sees, not a UI string the frontend could
 * translate on its own. If a second language ever arrives, these move to a resource bundle and the
 * label becomes a key — the shape of {@link RoomListDeriver} does not change.
 */
final class RoomLabels {

	private RoomLabels() {}

	static String of(RoomType type) {
		return switch (type) {
			case LIVING_ROOM -> "Salon";
			case MASTER_BEDROOM -> "Ebeveyn yatak odası";
			case BEDROOM -> "Yatak odası";
			case STUDY -> "Çalışma odası";
			case KITCHEN -> "Mutfak";
			case BATHROOM -> "Banyo";
			case HALLWAY -> "Koridor";
			case BALCONY -> "Balkon";
		};
	}
}
