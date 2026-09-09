package com.burakpadr.decorating.quoting.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.quoting.domain.model.CeilingFinding;
import com.burakpadr.decorating.quoting.domain.model.Coating;
import com.burakpadr.decorating.quoting.domain.model.CrackLevel;
import com.burakpadr.decorating.quoting.domain.model.FillerBand;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.Review;
import com.burakpadr.decorating.quoting.domain.model.ReviewDecision;
import com.burakpadr.decorating.quoting.domain.model.ReviewInput;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.SurfaceFinding;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §6's decision thresholds, branch by branch (workflow §4.3, BOYA-51).
 *
 * <p>Pure, like the engine and for the same reason: this is the gate between a home the system will
 * price from photographs and a home somebody has to visit, and every branch of it is a different
 * afternoon for a customer. §6 writes the rule as five ordered lines and this file is those five lines
 * plus every risk finding, one case each — which is what the card asks for.
 *
 * <p>The order is load-bearing and tested. RECAPTURE comes before the risk findings because a frame
 * nobody could read is not evidence of anything: deciding a survey from an analysis whose photographs
 * were unusable is deciding from noise.
 */
class ConfidenceEvaluatorTest {

	private static final BigDecimal AVERAGE_JOB = new BigDecimal("25000.00");
	private static final BigDecimal SURVEY_FACTOR = new BigDecimal("2.00");

	private final ConfidenceEvaluator evaluator = new ConfidenceEvaluator();

	// -----------------------------------------------------------------------------------------------
	// The five lines of §6, in order
	// -----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a clean, confident, ordinary reading goes to the operator's queue")
	void autoWhenNothingIsWrong() {
		Review review = evaluator.evaluate(input(sound("0.900")).build());

		assertThat(review.decision()).isEqualTo(ReviewDecision.AUTO);
		assertThat(review.reasons()).isEmpty();
	}

	@Test
	@DisplayName("an unusable frame asks for it again — once")
	void recaptureWhenAFrameCouldNotBeUsed() {
		Review review = evaluator.evaluate(
				input(unusable(sound("0.900"), "WALL_2")).recaptureCount(0).build());

		assertThat(review.decision()).isEqualTo(ReviewDecision.RECAPTURE);
		assertThat(review.reasons()).anyMatch(reason -> reason.contains("WALL_2"));
	}

	@Test
	@DisplayName("asked once already, the second unusable set is the operator's problem")
	void surveyWhenTheRecaptureWasAlreadySpent() {
		// §6: "recapture is requested once only. A second failure goes to the operator." Asking twice
		// tells the customer the system cannot read their home, and they would be right.
		Review review = evaluator.evaluate(
				input(unusable(sound("0.900"), "WALL_2")).recaptureCount(1).build());

		assertThat(review.decision()).isEqualTo(ReviewDecision.SURVEY);
		assertThat(review.reasons()).anyMatch(reason -> reason.contains("ikinci"));
	}

	@Test
	@DisplayName("below 0.65 the reading is not worth pricing from")
	void surveyBelowTheHardConfidenceFloor() {
		Review review = evaluator.evaluate(input(sound("0.600")).build());

		assertThat(review.decision()).isEqualTo(ReviewDecision.SURVEY);
		assertThat(review.reasons()).anyMatch(reason -> reason.contains("güven"));
	}

	@Test
	@DisplayName("between 0.65 and 0.80, only an expensive job is visited")
	void surveyForAnUncertainExpensiveJob() {
		// §6's fourth line. An uncertain cheap job is a small mistake; an uncertain expensive one is the
		// mistake this threshold exists for. The bar is average × factor — 50.000 here.
		ReviewInput.Builder uncertain = input(sound("0.700"));

		assertThat(evaluator.evaluate(uncertain.estimatedTotal("49000").build()).decision())
				.isEqualTo(ReviewDecision.AUTO);
		assertThat(evaluator.evaluate(uncertain.estimatedTotal("51000").build()).decision())
				.isEqualTo(ReviewDecision.SURVEY);
	}

	@Test
	@DisplayName("0.80 and above is confident, whatever the job is worth")
	void doesNotVisitAConfidentExpensiveJob() {
		Review review = evaluator.evaluate(
				input(sound("0.800")).estimatedTotal("500000").build());

		assertThat(review.decision()).isEqualTo(ReviewDecision.AUTO);
	}

	// -----------------------------------------------------------------------------------------------
	// Every risk finding
	// -----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("active damp on a wall is a survey however confident the reading is")
	void surveyForActiveMoisture() {
		Review review = evaluator.evaluate(input(
				room("0.990", surface(Moisture.ACTIVE, CrackLevel.NONE, false, "0.990"))).build());

		assertThat(review.decision()).isEqualTo(ReviewDecision.SURVEY);
		assertThat(review.reasons()).anyMatch(reason -> reason.contains("nem"));
	}

	@Test
	@DisplayName("water coming through the ceiling asks CeilingFinding, not §5.9's surface list")
	void surveyForAnActivelyLeakingCeiling() {
		// The finding BOYA-11a caught: §5.9 said "any surface moisture == ACTIVE" and a ceiling is not a
		// surface, so a leaking ceiling was priced automatically. ADR 0017 put the predicate on
		// CeilingFinding so it would be asked rather than re-derived — this is the caller it waited for.
		Review review = evaluator.evaluate(input(
				new RoomAnalysis(Uuid7.generate(), "v1", "m", "{}",
						List.of(surface(Moisture.NONE, CrackLevel.NONE, false, "0.950")),
						new CeilingFinding(Moisture.ACTIVE, FillerBand.LOW), new BigDecimal("0.950"),
						false, 0, Furnishing.EMPTY, 1, 1, 1, new BigDecimal("0.950"),
						List.of(), List.of())).build());

		assertThat(review.decision()).isEqualTo(ReviewDecision.SURVEY);
		assertThat(review.reasons()).anyMatch(reason -> reason.contains("Tavan"));
	}

	@Test
	@DisplayName("a structural crack is a survey")
	void surveyForAStructuralCrack() {
		Review review = evaluator.evaluate(input(
				room("0.990", surface(Moisture.NONE, CrackLevel.STRUCTURAL, false, "0.990"))).build());

		assertThat(review.decision()).isEqualTo(ReviewDecision.SURVEY);
		assertThat(review.reasons()).anyMatch(reason -> reason.contains("çatlak"));
	}

	@Test
	@DisplayName("skim coat over more than 40% of the wall is a survey, and 40% exactly is not")
	void surveyWhenTooMuchOfTheWallNeedsSkimming() {
		// §6's own figure. Below it the engine's skim line is a price; above it the job is a plastering
		// job with painting after, and that is not what was quoted for.
		ReviewInput.Builder heavy = input(sound("0.900"));

		assertThat(evaluator.evaluate(heavy.skim("40.00", "100.00").build()).decision())
				.isEqualTo(ReviewDecision.AUTO);
		assertThat(evaluator.evaluate(heavy.skim("41.00", "100.00").build()).decision())
				.isEqualTo(ReviewDecision.SURVEY);
		assertThat(evaluator.evaluate(heavy.skim("41.00", "100.00").build()).reasons())
				.anyMatch(reason -> reason.contains("saten alçı"));
	}

	@Test
	@DisplayName("a room missing a frame it was asked for is a survey")
	void surveyForAMissingFrame() {
		// Capture was complete at submission, so this is the defensive case: a frame deleted afterwards,
		// or a recapture the customer never finished. §6 lists it as a risk finding rather than an error
		// because the analysis it produced is real — it is just about less of the room than it claims.
		Review review = evaluator.evaluate(
				input(sound("0.900")).roomsMissingFrames(List.of("Salon")).build());

		assertThat(review.decision()).isEqualTo(ReviewDecision.SURVEY);
		assertThat(review.reasons()).anyMatch(reason -> reason.contains("Salon"));
	}

	@Test
	@DisplayName("a risk finding is not a recapture: the frames were readable, the home is not")
	void prefersRecaptureOverARiskFinding() {
		// Order matters and §6 fixes it. A frame nobody could read is not evidence, so deciding to visit
		// a home from an analysis whose photographs were unusable is deciding from noise — ask again
		// first, and only then believe what comes back.
		Review review = evaluator.evaluate(unusableAndRisky());

		assertThat(review.decision()).isEqualTo(ReviewDecision.RECAPTURE);
	}

	@Test
	@DisplayName("every reason that applies is listed, not only the first")
	void reportsEveryReason() {
		// The operator screen shows flags at the top and approves in 60-90 seconds (workflow §5.1). One
		// flag out of three is a screen that hides two of them.
		Review review = evaluator.evaluate(input(
				room("0.500", surface(Moisture.ACTIVE, CrackLevel.STRUCTURAL, false, "0.500"))).build());

		assertThat(review.reasons()).hasSizeGreaterThanOrEqualTo(3);
	}

	// -----------------------------------------------------------------------------------------------

	private static ReviewInput.Builder input(RoomAnalysis... analyses) {
		return ReviewInput.builder()
				.analyses(List.of(analyses))
				.estimatedTotal("30000")
				.averageJobValue(AVERAGE_JOB)
				.surveyAmountFactor(SURVEY_FACTOR)
				.skim("0.00", "100.00")
				.recaptureCount(0)
				.roomsMissingFrames(List.of());
	}

	private ReviewInput unusableAndRisky() {
		return input(unusable(room("0.990",
				surface(Moisture.ACTIVE, CrackLevel.NONE, false, "0.990")), "WALL_2")).build();
	}

	/** A room read at one confidence throughout, so {@code roomConfidence()} is that number. */
	private static RoomAnalysis sound(String confidence) {
		return room(confidence, surface(Moisture.NONE, CrackLevel.NONE, false, confidence));
	}

	private static RoomAnalysis room(String confidence, SurfaceFinding... surfaces) {
		return new RoomAnalysis(Uuid7.generate(), "v1", "m", "{}", List.of(surfaces),
				CeilingFinding.none(), new BigDecimal(confidence), false, 0,
				Furnishing.EMPTY, 1, 1, 1, new BigDecimal(confidence), List.of(), List.of());
	}

	private static RoomAnalysis unusable(RoomAnalysis analysis, String... labels) {
		return new RoomAnalysis(analysis.roomId(), "v1", "m", "{}", analysis.surfaces(),
				analysis.ceiling(), analysis.ceilingConfidence(), false, 0, Furnishing.EMPTY, 1, 1, 1,
				analysis.reportedConfidence(), List.of(labels), List.of());
	}

	private static SurfaceFinding surface(
			Moisture moisture, CrackLevel crack, boolean skim, String confidence) {
		return new SurfaceFinding("WALL_1", Coating.PAINTED, Tone.LIGHT, FillerBand.NONE, skim,
				crack, moisture, false, new BigDecimal(confidence));
	}
}
