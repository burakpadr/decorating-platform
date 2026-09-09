package com.burakpadr.decorating.quoting.domain.service;

import com.burakpadr.decorating.quoting.domain.model.CrackLevel;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.Review;
import com.burakpadr.decorating.quoting.domain.model.ReviewDecision;
import com.burakpadr.decorating.quoting.domain.model.ReviewInput;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.SurfaceFinding;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * §6's decision: price this from the photographs, ask for one of them again, or go and look
 * (workflow §4.3, BOYA-51).
 *
 * <p>Pure, like {@code PricingEngine} and for a related reason. This is the gate between a home the
 * system prices from photographs and a home somebody drives across the city to, so every branch of it
 * has to be drivable from a test with no database and no provider — and each branch is a different
 * afternoon for a customer.
 *
 * <p><b>The order is the rule.</b> §6 writes five ordered lines and the order is not incidental.
 * RECAPTURE is asked before any risk finding is believed, because a frame nobody could read is not
 * evidence of anything: sending somebody to a home on the strength of an analysis whose photographs
 * were unusable is deciding from noise. Ask again first, then believe what comes back.
 *
 * <p><b>AUTO is not auto-send.</b> §6 says so in as many words: it means PENDING_REVIEW. The only
 * thing AUTO claims is that nothing here needs flagging before an operator looks. Automatic sending is
 * a phase 3 question and nobody has answered it.
 *
 * <p><b>Every reason, not the first.</b> The operator's screen puts the flags at the top and expects
 * to be approved from in 60–90 seconds (workflow §5.1). One flag out of three is a screen that hides
 * two.
 */
public final class ConfidenceEvaluator {

	private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

	/** §6: below this the reading is not worth pricing from at any job value. */
	private static final BigDecimal NOT_WORTH_PRICING = new BigDecimal("0.65");

	/** §6: above this the reading is confident, whatever the job is worth. */
	private static final BigDecimal CONFIDENT = new BigDecimal("0.80");

	/** §6: more skim coat than this and it is a plastering job with painting after. */
	private static final BigDecimal TOO_MUCH_SKIM = new BigDecimal("0.40");

	public Review evaluate(ReviewInput input) {
		List<String> unusable = input.analyses().stream()
				.flatMap(analysis -> analysis.unusablePhotos().stream())
				.toList();

		if (!unusable.isEmpty()) {
			if (input.recaptureCount() == 0) {
				return new Review(ReviewDecision.RECAPTURE,
						List.of("Kullanılamayan kare: " + String.join(", ", unusable)));
			}
			// §6: asked once only. A customer asked twice for the same photograph has been told the
			// system cannot read their home, and they would be right.
			return new Review(ReviewDecision.SURVEY, List.of(
					"Tekrar çekim sonrası ikinci kez kullanılamayan kare: "
							+ String.join(", ", unusable)));
		}

		List<String> reasons = new ArrayList<>(risks(input));
		BigDecimal confidence = overallConfidence(input);

		if (confidence.compareTo(NOT_WORTH_PRICING) < 0) {
			reasons.add("Analiz güveni düşük: " + confidence.setScale(2, RoundingMode.HALF_UP));
		}
		else if (confidence.compareTo(CONFIDENT) < 0 && expensive(input)) {
			// §6's fourth line. An uncertain cheap job is a small mistake; an uncertain expensive one is
			// the mistake the threshold exists for.
			reasons.add("Güven orta (" + confidence.setScale(2, RoundingMode.HALF_UP)
					+ ") ve tutar ortalama işin " + input.surveyAmountFactor() + " katının üzerinde");
		}

		return reasons.isEmpty() ? Review.auto() : new Review(ReviewDecision.SURVEY, reasons);
	}

	/**
	 * §5.9's {@code riskFinding} list, in the order §6 writes it.
	 *
	 * <p>The ceiling line is asked of {@code CeilingFinding.isRisk()} rather than re-derived here. ADR
	 * 0017 wrote that predicate deliberately without a caller: §5.9's list says "any <em>surface</em>
	 * moisture == ACTIVE", a ceiling is not a {@code surface_finding} row, and for the length of the
	 * project that sentence read past the one place a leak is most likely. This is the caller it was
	 * waiting for, and asking rather than restating is what stops the rule drifting back.
	 */
	private List<String> risks(ReviewInput input) {
		List<String> reasons = new ArrayList<>();

		if (anySurface(input, surface -> surface.moisture() == Moisture.ACTIVE)) {
			reasons.add("Duvarda aktif nem");
		}
		if (input.analyses().stream().anyMatch(analysis -> analysis.ceiling().isRisk())) {
			reasons.add("Tavanda aktif su sızıntısı");
		}
		if (anySurface(input, surface -> surface.crackLevel() == CrackLevel.STRUCTURAL)) {
			reasons.add("Yapısal çatlak");
		}
		if (skimShare(input).compareTo(TOO_MUCH_SKIM) > 0) {
			reasons.add("Duvarların %40'ından fazlası saten alçı istiyor");
		}
		for (String label : input.roomsMissingFrames()) {
			reasons.add("Eksik kare: " + label);
		}
		return reasons;
	}

	/**
	 * The confidence §6 thresholds against: the average of what each room reported.
	 *
	 * <p>Each room's own figure is decision 0021's — the average over every plane it read, ceiling
	 * included — so this is an average of averages and every room counts once. A home is not more
	 * confidently read because six of its seven rooms were easy.
	 *
	 * <p>Not the same number the engine widens the band with. §5.9's confidence term is computed inside
	 * the engine over {@code surface_finding} alone, because {@code RoomInput} carries no ceiling
	 * confidence to give it. The two are close and they are not equal, which is worth knowing before
	 * anybody compares a band to a decision (decision 0023).
	 */
	private BigDecimal overallConfidence(ReviewInput input) {
		BigDecimal sum = input.analyses().stream()
				.map(RoomAnalysis::roomConfidence)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return sum.divide(BigDecimal.valueOf(input.analyses().size()), MC);
	}

	private boolean expensive(ReviewInput input) {
		return input.estimatedTotal().compareTo(
				input.averageJobValue().multiply(input.surveyAmountFactor())) > 0;
	}

	private BigDecimal skimShare(ReviewInput input) {
		if (input.wallArea().signum() <= 0) {
			// No paintable wall at all: a fully tiled job has nothing for skim coat to be a share of, and
			// dividing would answer with an exception rather than a decision.
			return BigDecimal.ZERO;
		}
		return input.skimArea().divide(input.wallArea(), MC);
	}

	private boolean anySurface(ReviewInput input, java.util.function.Predicate<SurfaceFinding> test) {
		return input.analyses().stream().flatMap(analysis -> analysis.surfaces().stream()).anyMatch(test);
	}
}
