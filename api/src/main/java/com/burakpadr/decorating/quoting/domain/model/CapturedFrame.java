package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What the browser measured about a frame before sending it (§9, workflow §2.5).
 *
 * <p>Every field is nullable on purpose. The client reads EXIF, resizes on a canvas and computes a
 * Laplacian variance, and any of the three can fail on a phone that is old enough — none of which is a
 * reason to lose the photograph. §9 is explicit about the direction of that trade: "rejecting a good
 * photo is far more costly than accepting a mediocre one".
 *
 * @param capturedAt EXIF {@code DateTimeOriginal}, read before the re-encode strips it. Not the upload
 *     time: the two differ by however long the customer spent in the room.
 * @param qualityScore Laplacian variance, the client's own sharpness measure. What it decides is
 *     whether the customer is asked to shoot again while still standing in the room, which is the
 *     whole point of doing it there rather than hours later.
 * @param lowQualityFlag set when the frame was accepted despite the score — §9's third rejection of
 *     the same frame is accepted rather than fought over.
 */
public record CapturedFrame(
		Instant capturedAt,
		Integer width,
		Integer height,
		Integer byteSize,
		BigDecimal qualityScore,
		boolean lowQualityFlag) {

	public CapturedFrame {
		positive(width, "width");
		positive(height, "height");
		positive(byteSize, "byteSize");
		if (qualityScore != null && qualityScore.signum() < 0) {
			throw new IllegalArgumentException("a variance cannot be negative: " + qualityScore);
		}
	}

	private static void positive(Integer value, String field) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(field + " must be a positive number of pixels or bytes");
		}
	}
}
