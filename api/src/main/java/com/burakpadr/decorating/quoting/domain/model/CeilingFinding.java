package com.burakpadr.decorating.quoting.domain.model;

/**
 * What the analysis found overhead (§4.4, §5.6).
 *
 * <p>{@code room_analysis} has carried {@code ceiling_staining} and {@code ceiling_filler} since the
 * baseline migration, and §5.6's quantity table used neither: filler and stain block were both
 * {@code Σ wallNet}. So a stained ceiling produced no primer, in the one case where a decorator
 * certainly primes — a systematic underquote on exactly the jobs with a leak, invisible because the
 * engine still returned a number. BOYA-11a, {@code docs/decisions/0017}.
 *
 * <p>Its own record rather than two more components on {@link RoomInput}: the stage 2 factory has to
 * take it, and a caller that has to name the type it is passing cannot forget the ceiling the way §5.6
 * did. {@link #none()} is how a room says the ceiling is sound — never a default.
 *
 * <p>Stage 1 is always {@code none()}: §2.1's eight questions are about walls, and turning a wall
 * answer into a ceiling finding would be the engine deciding something nobody told it.
 */
public record CeilingFinding(Moisture staining, FillerBand filler) {

	public CeilingFinding {
		if (staining == null || filler == null) {
			throw new IllegalArgumentException(
					"a ceiling finding states both: use CeilingFinding.none() for a sound ceiling");
		}
	}

	/** A sound ceiling: nothing to fill, nothing to seal. */
	public static CeilingFinding none() {
		return new CeilingFinding(Moisture.NONE, FillerBand.NONE);
	}

	/**
	 * Whether this ceiling is the kind §5.9's {@code riskFinding} should stop rather than price. An
	 * active leak overhead is not a painting job until somebody has been up there, and §5.9's list
	 * reads "any SURFACE moisture == ACTIVE" — a ceiling is not a surface, so the rule misses it. The
	 * predicate lives here so the evaluator that gets written (BOYA-51) has one place to ask; the
	 * engine does not call it, because deciding to survey is not the engine's job.
	 */
	public boolean isRisk() {
		return staining == Moisture.ACTIVE;
	}
}
