package com.burakpadr.decorating.quoting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §6's room confidence, which is the one number the rest of the system asks this record for.
 *
 * <p>It widens the band (§5.9) and it decides between AUTO and SURVEY (§6), so what it is an average
 * <em>of</em> is a pricing decision rather than a formatting one. §6 says "the weighted average of the
 * surface confidences, not the minimum" and stops there — it names no weights, and none exist: §5.4
 * gives a room one wall plane, so there is no per-wall area to weigh by. The word is there to rule out
 * the minimum, and the rule below is the least-invented thing that does: every plane the model
 * reported counts once. Decision 0021.
 *
 * <p>The ceiling is one of those planes. §6 says surfaces and a ceiling is not a surface — the same
 * sentence that let an actively leaking ceiling price itself until BOYA-11a. Since ADR 0017 the
 * ceiling carries cost of its own, so a room read confidently at eye level and barely at all overhead
 * is not a confident room.
 */
class RoomAnalysisTest {

	@Test
	@DisplayName("the average of what was read, not the worst of it")
	void averagesRatherThanTakingTheMinimum() {
		RoomAnalysis analysis = analysis(ceiling("0.70"), "0.90", "0.80");

		assertThat(analysis.roomConfidence()).isEqualByComparingTo("0.800");
	}

	@Test
	@DisplayName("one blurry frame does not poison the room")
	void survivesASingleBadFrame() {
		// §6's own words. The minimum would answer 0.200 and send a well-photographed room to a survey
		// on the strength of one frame somebody took while walking.
		RoomAnalysis analysis = analysis(ceiling("0.95"), "0.95", "0.95", "0.95", "0.20");

		assertThat(analysis.roomConfidence()).isEqualByComparingTo("0.800");
	}

	@Test
	@DisplayName("an unreadable ceiling lowers the room, because the ceiling is priced")
	void countsTheCeiling() {
		// Two rooms with identical walls. Since ADR 0017 a stained ceiling takes stain-block primer over
		// its whole plane — real money on a real line — so the reading it came from cannot be free.
		RoomAnalysis clear = analysis(ceiling("0.90"), "0.90", "0.90");
		RoomAnalysis overhead = analysis(ceiling("0.30"), "0.90", "0.90");

		assertThat(clear.roomConfidence()).isEqualByComparingTo("0.900");
		assertThat(overhead.roomConfidence()).isEqualByComparingTo("0.700");
	}

	@Test
	@DisplayName("a corner-shot room averages its one surface with its ceiling")
	void handlesASingleSurface() {
		// Kitchens, bathrooms and hallways report one ROOM_GENERAL surface (§4.4), so the ceiling is half
		// of what was read rather than a fifth. That is not a special case — it is the same rule over
		// fewer readings, and there genuinely are fewer.
		RoomAnalysis analysis = analysis(ceiling("0.80"), "0.60");

		assertThat(analysis.roomConfidence()).isEqualByComparingTo("0.700");
	}

	@Test
	@DisplayName("rounded to the three decimals the column holds")
	void roundsToTheColumn() {
		// room_analysis.confidence is numeric(4,3). Rounding here rather than letting the insert do it
		// keeps the value the evaluator reads and the value the row holds the same number.
		RoomAnalysis analysis = analysis(ceiling("0.75"), "0.88", "0.79");

		assertThat(analysis.roomConfidence()).isEqualByComparingTo("0.807");
	}

	@Test
	@DisplayName("an analysis states what it could see overhead")
	void refusesAMissingCeilingConfidence() {
		assertThatThrownBy(() -> analysis(null, "0.90"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// -----------------------------------------------------------------------------------------------

	private static RoomAnalysis analysis(BigDecimal ceilingConfidence, String... surfaceConfidences) {
		List<SurfaceFinding> surfaces = Arrays.stream(surfaceConfidences)
				.map(RoomAnalysisTest::surface)
				.toList();

		return new RoomAnalysis(Uuid7.generate(), "v1", "test-model", "{}",
				surfaces, CeilingFinding.none(), ceilingConfidence, true, 4,
				Furnishing.EMPTY, 1, 1, 1, new BigDecimal("0.50"), List.of(), List.of());
	}

	private static BigDecimal ceiling(String confidence) {
		return new BigDecimal(confidence);
	}

	private static SurfaceFinding surface(String confidence) {
		return new SurfaceFinding("WALL_1", Coating.PAINTED, Tone.LIGHT, FillerBand.NONE,
				false, CrackLevel.NONE, Moisture.NONE, false, new BigDecimal(confidence));
	}
}
