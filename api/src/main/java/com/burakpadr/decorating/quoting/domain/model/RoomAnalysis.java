package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What the model saw in one room (§4.4 {@code room_analysis}, §6).
 *
 * <p>Observations, never quantities. Every field here is something a photograph showed; not one of
 * them is a square metre, a duration or a sum, and the schema the response was validated against
 * forbids the model from volunteering any.
 *
 * <p>{@code rawResponse} is kept whole alongside the parsed fields, which §4.4 asks for and is worth
 * restating: it is the audit trail, it is what makes two prompt versions comparable, and it is where
 * the handful of reported values with no column of their own — the ceiling's own confidence — remain
 * readable. The parsed fields are what the engine and the calibration queries read, because neither
 * may parse JSON.
 *
 * <p>{@code promptVersion} and {@code modelVersion} are mandatory and enforced here rather than
 * trusted. §4.4: without the prompt version, results from different prompts become incomparable and
 * the calibration data is worthless — and a row is written once, so the moment to refuse an
 * unattributed analysis is before it is stored, not when somebody comes to compare them.
 *
 * <p>{@code reportedConfidence} is the model's own figure for the room. It is not
 * {@code room_analysis.confidence}: §6 defines that as the weighted average of the surface
 * confidences, so that one blurry frame does not poison a room, and BOYA-49 is what derives it when it
 * writes the row. Two numbers, kept apart on purpose — the model's self-assessment is evidence, not
 * the answer.
 */
public record RoomAnalysis(
		UUID roomId,
		String promptVersion,
		String modelVersion,
		String rawResponse,
		RoomType roomType,
		List<SurfaceFinding> surfaces,
		CeilingFinding ceiling,
		boolean cornice,
		int downlightCount,
		Furnishing furnishing,
		int doorCount,
		int windowCount,
		int radiatorCount,
		BigDecimal reportedConfidence,
		List<String> unusablePhotos,
		List<String> notes) {

	public RoomAnalysis {
		surfaces = List.copyOf(surfaces);
		unusablePhotos = List.copyOf(unusablePhotos);
		notes = List.copyOf(notes);
		if (roomId == null || roomType == null || ceiling == null || furnishing == null
				|| reportedConfidence == null || rawResponse == null) {
			throw new IllegalArgumentException("an incomplete analysis is not an analysis");
		}
		if (blank(promptVersion) || blank(modelVersion)) {
			throw new IllegalArgumentException(
					"an analysis records which prompt and which model produced it (§4.4)");
		}
		if (surfaces.isEmpty()) {
			throw new IllegalArgumentException("an analysis with no surface describes no room");
		}
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
