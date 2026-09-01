package com.burakpadr.decorating.quoting.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Everything the model is given about one room, in one call (§6, workflow §4.1).
 *
 * <p>One call per room and every frame of that room inside it, close-ups included. §6 is explicit
 * about why: per-photo calls cannot tell that the crack in {@code WALL_2} and the crack in
 * {@code DETAIL_1} are the same crack, so they count it twice, and they cannot keep tone consistent
 * across four walls of the same room. A 3+1 home is therefore seven calls, and a failure retries one
 * room rather than a house.
 *
 * <p>Job granularity follows from that shape, which is why {@code analysis_job.room_id} is a room and
 * not a request.
 */
public record RoomAnalysisRequest(UUID roomId, RoomType roomType, List<LabelledPhoto> photos) {

	public RoomAnalysisRequest {
		photos = List.copyOf(photos);
	}

	/**
	 * The room's frames, named the way {@code v1.md} promises they will be.
	 *
	 * <p>Order is fixed rather than however the rows arrived: walls, then the ceiling, then the
	 * close-ups. A model reads its context in order, and "the fourth wall" meaning a different wall
	 * between two runs would make two analyses of one room incomparable — which is the same reason
	 * {@code prompt_version} is recorded.
	 *
	 * <p>Frames the browser never uploaded are left out. A reserved key with no object behind it
	 * presigns to a URL that 404s, and a model shown an image it cannot fetch does not say so — it
	 * describes the others, which reads downstream like a wall with nothing wrong with it. Whether the
	 * room is missing a frame it was required to have is a §5.9 risk finding and the evaluator's
	 * question (BOYA-51); it is not a reason to withhold the frames that did arrive.
	 */
	public static RoomAnalysisRequest of(UUID roomId, RoomType roomType, List<Photo> photos) {
		List<Photo> arrived = photos.stream()
				.filter(Photo::isUploaded)
				.sorted(Comparator.comparing((Photo photo) -> photo.role().ordinal())
						// Close-ups are unbounded, so they are the only role that needs an order among
						// itself. The id gives a total one that does not change: UUIDv7 is time-ordered to
						// the millisecond, so DETAIL_1 is the first close-up the customer took — and even
						// where two ids shared a millisecond, which two photographs do not, the order
						// would still be the same on every call. Stability is the requirement; chronology
						// is the consequence.
						.thenComparing(Photo::id))
				.toList();

		if (arrived.isEmpty()) {
			// A model asked about a room it cannot see still answers, and the answer is a plausible
			// average room. That is an invention with a price on it and nothing downstream can tell it
			// from an observation, so it is refused here rather than paid for.
			throw new IllegalArgumentException("no frame of room " + roomId + " has been uploaded");
		}

		List<LabelledPhoto> labelled = new ArrayList<>(arrived.size());
		int closeUps = 0;
		for (Photo photo : arrived) {
			String label = photo.role().isRepeatable()
					? photo.role().name() + "_" + ++closeUps
					: photo.role().name();
			labelled.add(new LabelledPhoto(label, photo.id(), photo.storageKey()));
		}
		return new RoomAnalysisRequest(roomId, roomType, labelled);
	}
}
