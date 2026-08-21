package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.DistrictNotServed;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculation;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculationCommand;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.StageOneEstimate;
import com.burakpadr.decorating.quoting.domain.port.in.CalculateQuote;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.StageOneEstimateWriter;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 1's range for a stored draft (BOYA-29).
 *
 * <p>It calls the same {@link CalculateQuote} the operator's tool calls. That is the whole design: two
 * screens asking one question, so there is one answer. A second derive-and-price path here would be a
 * second answer to "what does this job cost", and the two would drift the first time one of them was
 * corrected — which is the failure this codebase has already had once, in the price book (ADR 0016).
 *
 * <p>What differs is on the way out. The operator gets the cost and the margin because comparing them
 * is the point of that tool; a customer gets the range, the areas assumed, and how wide the band is and
 * why (§1, workflow §1.5).
 */
@Service
class StageOneEstimateService implements EstimateStageOne {

	private final QuoteRequestRepository requests;
	private final CalculateQuote calculator;
	private final StageOneEstimateWriter estimates;
	private final PriceBookRepository priceBooks;

	StageOneEstimateService(QuoteRequestRepository requests, CalculateQuote calculator,
			StageOneEstimateWriter estimates, PriceBookRepository priceBooks) {
		this.requests = requests;
		this.calculator = calculator;
		this.estimates = estimates;
		this.priceBooks = priceBooks;
	}

	@Override
	@Transactional
	public StageOneEstimate estimate(UUID id) {
		QuoteRequest request = requests.findById(id)
				.orElseThrow(() -> new QuoteRequestNotFound(id.toString()));
		StageOneAnswers answers = request.answers();
		if (!answers.isPriceable()) {
			// Named rather than counted: "some answers are missing" sends the customer back to look at
			// three screens. The form knows which question is which, and priceable is what it asks.
			throw new IllegalStateException(
					"this draft cannot be priced yet: §2.1's questions are not all answered");
		}

		// Checked again here, and not only when the district was answered: a draft can sit for days and a
		// district can be switched off in between. PriceBook.districtFactor prices an unlisted district at
		// 1.0000 by design (the operator tool quotes hypothetical addresses), so without this the customer
		// would be quoted for an area nobody will drive to.
		if (!priceBooks.findActive().orElseThrow().serves(answers.districtCode())) {
			throw new DistrictNotServed(answers.districtCode());
		}

		QuoteCalculation calculation = calculator.calculate(new QuoteCalculationCommand(
				answers.districtCode(),
				answers.areaInput(),
				answers.areaBasis(),
				answers.layout(),
				answers.scope(),
				answers.selectedRooms() == null ? Set.of() : answers.selectedRooms(),
				answers.wallCondition(),
				answers.furnishing(),
				// Stage 1 does not ask these three. Zero doors when the customer did not say, no rush, and
				// a lift assumed present — §5.6 charges for the absence of one, so assuming it is there is
				// the assumption that cannot flatter the price.
				answers.doorCount() == null ? 0 : answers.doorCount(),
				Boolean.TRUE.equals(answers.doorColourChange()),
				false,
				true,
				false));

		estimates.recordEstimate(
				id,
				calculation.netArea(),
				// §4.5: the range the customer saw has to stay explainable after the next zam, so the row
				// remembers which version produced it rather than which one is active when somebody asks.
				calculation.priceBookVersion(),
				calculation.quote().bandLow(),
				calculation.quote().bandHigh());

		return new StageOneEstimate(
				calculation.quote().bandLow(),
				calculation.quote().bandHigh(),
				calculation.quote().bandRatio(),
				calculation.netArea(),
				calculation.areaWasGross(),
				calculation.rooms(),
				calculation.priceBookVersion());
	}
}
