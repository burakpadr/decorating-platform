package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * One frame of one room (§4.3, §9, BOYA-40).
 *
 * <p>The photograph itself never passes through the JVM. The browser is handed a presigned PUT and
 * uploads straight to storage, so what this type holds is the part the server does know: where the
 * object will live, which frame of which room it is, and — once the client says so — what it measured
 * on the way out.
 *
 * <p>A row therefore has two lives. {@link #intended} is a reservation: a key nobody has written to
 * yet, and a promise the browser may never keep. {@link #uploaded} is the same row after the object
 * arrived. Keeping them one type rather than two is deliberate — the key has to be decided before the
 * URL can be signed, and a second type would mean the key was invented twice.
 */
public record Photo(
		UUID id,
		UUID roomId,
		PhotoRole role,
		String storageKey,
		Instant capturedAt,
		Instant uploadedAt,
		Integer width,
		Integer height,
		Integer byteSize,
		BigDecimal qualityScore,
		boolean lowQualityFlag) {

	/**
	 * The largest sharpness the column can hold.
	 *
	 * <p>{@code quality_score} is {@code numeric(5,2)} and a Laplacian variance has no upper bound, so
	 * the sharpest frames are exactly the ones that would overflow it. Clamped rather than refused: §9
	 * would rather keep a mediocre photograph than lose a good one, and losing the sharpest of them to
	 * an arithmetic overflow is that rule stood on its head.
	 */
	private static final BigDecimal SHARPEST_THE_COLUMN_HOLDS = new BigDecimal("999.99");

	public Photo {
		if (id == null || roomId == null || role == null || storageKey == null) {
			throw new IllegalArgumentException("a photo is a role, a room and a key");
		}
	}

	/**
	 * The object key §9 specifies: {@code quotes/{quoteRequestId}/{roomId}/{photoId}.jpg}.
	 *
	 * <p>Three UUIDv7s deep. The bucket is private and every read is presigned, so the key is not a
	 * secret — but it is also not a number anybody can count up through, which is what stops one
	 * customer's key from being a short walk from another's.
	 */
	public static String keyFor(UUID quoteRequestId, UUID roomId, UUID photoId) {
		return "quotes/" + quoteRequestId + "/" + roomId + "/" + photoId + ".jpg";
	}

	/** A reserved key and nothing else yet: the browser has been handed a URL it may never use. */
	public static Photo intended(UUID id, UUID quoteRequestId, UUID roomId, PhotoRole role) {
		return new Photo(id, roomId, role, keyFor(quoteRequestId, roomId, id),
				null, null, null, null, null, null, false);
	}

	public boolean isUploaded() {
		return uploadedAt != null;
	}

	/** The same row once the object has arrived, carrying what the client measured. */
	public Photo uploaded(Instant at, CapturedFrame frame) {
		if (at == null) {
			throw new IllegalArgumentException("an upload happened at a time");
		}
		return new Photo(id, roomId, role, storageKey,
				frame.capturedAt(), at, frame.width(), frame.height(), frame.byteSize(),
				clamp(frame.qualityScore()), frame.lowQualityFlag());
	}

	private static BigDecimal clamp(BigDecimal score) {
		if (score == null) {
			return null;
		}
		BigDecimal rounded = score.setScale(2, RoundingMode.HALF_UP);
		return rounded.compareTo(SHARPEST_THE_COLUMN_HOLDS) > 0 ? SHARPEST_THE_COLUMN_HOLDS : rounded;
	}
}
