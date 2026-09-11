package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.DistrictNotServed;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculation;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculationCommand;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.model.RoomTypeConfig;
import com.burakpadr.decorating.quoting.domain.model.SetupIncomplete;
import com.burakpadr.decorating.quoting.domain.model.SetupStatus;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.StageOneEstimate;
import com.burakpadr.decorating.quoting.domain.port.in.CalculateQuote;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.StageOneEstimateWriter;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

		// The book the calculator is about to price against, read once: the district check and §2.2's
		// frames per kind of area are two questions about the same version, and asking twice would let a
		// zam land between them.
		PriceBook book = priceBooks.findActive()
				// Before the calculator's own check, because this method reads the book for the district
				// question first. Same refusal either way (BOYA-69): a range from an install whose figures
				// nobody entered is a number the customer will hold the business to.
				.orElseThrow(() -> new SetupIncomplete(SetupStatus.of(Optional.empty())));

		// Checked again here, and not only when the district was answered: a draft can sit for days and a
		// district can be switched off in between. PriceBook.districtFactor prices an unlisted district at
		// 1.0000 by design (the operator tool quotes hypothetical addresses), so without this the customer
		// would be quoted for an area nobody will drive to.
		if (!book.serves(answers.districtCode())) {
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
				answers.doorCount() == null ? 0 : answers.doorCount(),
				Boolean.TRUE.equals(answers.doorColourChange()),
				// The three §2.1 does not ask, from the one place they are written — stage 2 prices from
				// the same answers and a fourth boolean here would be a silent difference between the
				// range a customer saw and the quote they are sent.
				StageOneAnswers.UNASKED_DOOR_COUNT_ESTIMATED,
				StageOneAnswers.UNASKED_HAS_ELEVATOR,
				StageOneAnswers.UNASKED_RUSH));

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
				// Every kind of area, not only the ones this layout derived: workflow §2.2's add-an-area
				// buttons offer the ones it did not, and the screen still owes the customer a true total.
				book.roomTypes().values().stream().collect(Collectors.toMap(
						RoomTypeConfig::roomType,
						config -> config.requiredPhotos().size())),
				calculation.priceBookVersion());
	}
}
