package com.burakpadr.decorating.quoting.domain.model;

import java.util.List;

/**
 * One room of the input (§5.1). The same record for both stages, because §5.1 is explicit that stage 1
 * and stage 2 build the same object and the engine must not know which produced it.
 *
 * <p>Stage 1 fills {@code declaredCondition} and leaves {@code surfaces} empty: the customer said what
 * shape the walls are in and §5.6 turns that into synthetic findings. Stage 2 fills {@code surfaces}
 * from the analysis and leaves {@code declaredCondition} null, because a finding beats a declaration.
 *
 * <p>The counts are per room and they deduct openings (§5.5). They are not what {@code DOOR_PAINT} is
 * priced on — that is the customer's declared total, which §5.6 keeps at the top level.
 *
 * <p>{@code downlightCount} and {@code cornice} are not in §5.1's record, but §5.6 prices both and
 * {@code room_analysis} carries both. §5.1 is the section that is short.
 */
public record RoomInput(
		RoomType type,
		WallCondition declaredCondition,
		List<SurfaceInput> surfaces,
		int doorCount,
		int windowCount,
		int radiatorCount,
		int downlightCount,
		boolean cornice) {

	public RoomInput {
		surfaces = List.copyOf(surfaces);
	}

	/** A stage 1 room: a type and what the customer said about the walls. */
	public static RoomInput declared(RoomType type, WallCondition condition) {
		return new RoomInput(type, condition, List.of(), 0, 0, 0, 0, false);
	}

	/** A stage 2 room: what the analysis found, plus what it counted. */
	public static RoomInput analysed(
			RoomType type,
			List<SurfaceInput> surfaces,
			int doorCount,
			int windowCount,
			int radiatorCount,
			int downlightCount,
			boolean cornice) {
		return new RoomInput(
				type, null, surfaces, doorCount, windowCount, radiatorCount, downlightCount, cornice);
	}
}
