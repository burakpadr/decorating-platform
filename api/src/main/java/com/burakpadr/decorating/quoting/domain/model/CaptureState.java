package com.burakpadr.decorating.quoting.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * How far a capture has got: the agreed areas, and which of their frames have arrived (§2.4, BOYA-42).
 *
 * <p>A read model rather than an aggregate. Nothing here transitions anything — {@code room} and
 * {@code photo} own the facts and this is the join of them the capture screen needs, which is a
 * question no existing type answered: {@code ConfirmedRooms} knows what was agreed and {@code Photo}
 * knows what arrived, and the screen has to show both against each other.
 *
 * <p>§2.6's close-ups are carried separately, in {@code extras}. They belong to no slot — unlimited
 * and skippable, taken because the customer saw a crack — so they cannot be frames without either
 * inventing slots for them or making a room's completeness depend on how much was wrong with it.
 * Counting them would say a capture that found two problems is further along than one that found
 * none, and neither is more finished than the other.
 *
 * <p>The line that carries the weight is {@link CaptureFrame#taken}. A reserved frame and a
 * photographed one are different things — an intent is a signed URL and a promise, and a lift with no
 * signal breaks the promise while leaving the row. Counting reservations would tell somebody they had
 * finished with a half-empty bucket, and §3's submit arrow would then refuse for reasons the screen
 * never showed them.
 */
public record CaptureState(List<CaptureArea> areas) {

	public CaptureState {
		areas = List.copyOf(areas);
	}

	/** One agreed area and the frames §2.4's table asks of it. */
	public record CaptureArea(UUID id, RoomType type, String label, int sortOrder,
			List<CaptureFrame> frames, List<CaptureExtra> extras) {

		public CaptureArea {
			frames = List.copyOf(frames);
			extras = List.copyOf(extras);
		}

		public boolean complete() {
			return frames.stream().allMatch(CaptureFrame::taken);
		}

		public int taken() {
			return (int) frames.stream().filter(CaptureFrame::taken).count();
		}
	}

	/**
	 * A close-up somebody chose to take (§2.6).
	 *
	 * @param lowQualityFlag carried like a frame's, because a blurred close-up of a crack is worth
	 *     knowing about: it is the frame the analysis was most likely to learn something from.
	 */
	public record CaptureExtra(UUID photoId, boolean lowQualityFlag) {}

	/**
	 * One frame of one area.
	 *
	 * @param photoId the row this frame's photograph lives in, or null if it has not arrived. Carried so
	 *     a retake can delete the object before reserving another — §9 refuses to replace an uploaded
	 *     frame in place, because that would leave bytes in the bucket with no row naming them.
	 */
	public record CaptureFrame(PhotoRole role, UUID photoId, boolean taken, boolean lowQualityFlag) {

		public static CaptureFrame outstanding(PhotoRole role) {
			return new CaptureFrame(role, null, false, false);
		}
	}

	public int required() {
		return areas.stream().mapToInt(area -> area.frames().size()).sum();
	}

	public int taken() {
		return areas.stream().mapToInt(CaptureArea::taken).sum();
	}

	/**
	 * Every frame of every area is in.
	 *
	 * <p>False for a request with no areas at all. A capture that was never agreed is not a finished
	 * one, and {@code allMatch} over nothing would say it was.
	 */
	public boolean complete() {
		return !areas.isEmpty() && areas.stream().allMatch(CaptureArea::complete);
	}
}
