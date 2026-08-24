package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.CapturedFrame;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * What the client measured before it uploaded (§9's pipeline, steps 1 and 4).
 *
 * <p>Every field is optional because every one of them can fail on a phone that is old enough: EXIF
 * that is not there, a canvas that will not measure, a variance nothing computed. §9 would rather have
 * the photograph than the measurements, so none of these is a condition of accepting it.
 */
record CompleteUploadRequest(
		@Schema(description = "EXIF DateTimeOriginal, read before the re-encode strips it")
		Instant capturedAt,
		Integer width,
		Integer height,
		Integer byteSize,
		@Schema(description = "Laplacian variance the client computed")
		BigDecimal qualityScore,
		@Schema(description = "Accepted despite the score — §9 stops arguing after three attempts")
		Boolean lowQualityFlag) {

	CapturedFrame toFrame() {
		return new CapturedFrame(capturedAt, width, height, byteSize, qualityScore,
				Boolean.TRUE.equals(lowQualityFlag));
	}
}
