package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * restating: it is the audit trail and it is what makes two prompt versions comparable. The parsed
 * fields are what the engine and the calibration queries read, because neither may parse JSON.
 *
 * <p>{@code promptVersion} and {@code modelVersion} are mandatory and enforced here rather than
 * trusted. §4.4: without the prompt version, results from different prompts become incomparable and
 * the calibration data is worthless — and a row is written once, so the moment to refuse an
 * unattributed analysis is before it is stored, not when somebody comes to compare them.
 *
 * <p>Which room this is does not appear here. The model reports a {@code roomType} and §4.4 gives the
 * row no column for it, because {@code room.room_type} is what the customer said and §5.3 prices that.
 * The reading stays in {@code rawResponse} as evidence rather than becoming a second answer to a
 * question already answered — asking for it is still worth the tokens, because a model made to commit
 * to what it is looking at reads the rest of the room better.
 *
 * <p>Two confidences, kept apart on purpose. {@code reportedConfidence} is the model's own figure for
 * the room — evidence, and the scalar a prompt-version comparison actually wants ("did v2 grow more or
 * less sure of itself"). {@link #roomConfidence()} is §6's rule and is the number the rest of the
 * system acts on: it widens the band (§5.9) and it decides between AUTO and SURVEY. A self-assessment
 * is not an assessment.
 */
public record RoomAnalysis(
		UUID roomId,
		String promptVersion,
		String modelVersion,
		String rawResponse,
		List<SurfaceFinding> surfaces,
		CeilingFinding ceiling,
		BigDecimal ceilingConfidence,
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
		if (roomId == null || ceiling == null || ceilingConfidence == null
				|| furnishing == null || reportedConfidence == null || rawResponse == null) {
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

	/**
	 * §6's room confidence: the average of every plane the model read, rounded to the three decimals
	 * {@code room_analysis.confidence} holds.
	 *
	 * <p>"The weighted average of the surface confidences, not the minimum" is all §6 says, and it names
	 * no weights because none are available: §5.4 gives a room a single wall plane, so there is no
	 * per-wall area to weigh by. The word rules out the minimum — one blurry frame must not poison a
	 * room — and every plane counting once is the least-invented rule that obeys it.
	 *
	 * <p>The ceiling is one of the planes, which is a deliberate step past §6's "surfaces". A ceiling is
	 * not a {@code surface_finding} row, and that same sentence is what let an actively leaking one
	 * price itself until BOYA-11a. Since ADR 0017 the ceiling carries cost of its own, so how well it
	 * was read belongs in how well the room was read. Decision 0021.
	 */
	public BigDecimal roomConfidence() {
		BigDecimal total = ceilingConfidence;
		for (SurfaceFinding surface : surfaces) {
			total = total.add(surface.confidence());
		}
		return total.divide(BigDecimal.valueOf(surfaces.size() + 1L), 3, RoundingMode.HALF_UP);
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}
}
