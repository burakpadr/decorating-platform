package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.CaptureState;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.Quote;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.model.QuoteStatus;
import com.burakpadr.decorating.quoting.domain.model.Review;
import com.burakpadr.decorating.quoting.domain.model.ReviewInput;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.port.in.ConcludeAnalysis;
import com.burakpadr.decorating.quoting.domain.port.in.GenerateQuote;
import com.burakpadr.decorating.quoting.domain.port.in.ReadCaptureState;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.ReviewOutcomes;
import com.burakpadr.decorating.quoting.domain.port.out.RoomAnalysisRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import com.burakpadr.decorating.quoting.domain.service.ConfidenceEvaluator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 4's ending: price the findings, read them, and move the request (workflow §4.2–4.3, BOYA-51).
 *
 * <p>Runs after every room's analysis and does nothing until the last one has landed. Same
 * transaction as that analysis, so a request either has a quote and a decision or has neither —
 * committed apart, the gap is a home whose rooms are all analysed and whose status says it is still
 * being worked on, which nothing would ever come back to.
 *
 * <p><b>SURVEY is a flag on the queue, not a status.</b> §6 reads as though it were a state, but §3
 * draws SURVEY_REQUIRED from PENDING_REVIEW and not from ANALYZING, and workflow §4.3 says the survey
 * case "kuyruğa keşif işaretiyle düşer" — it lands in the operator's queue with a survey mark. So AUTO
 * and SURVEY both go to PENDING_REVIEW and what separates them is {@code review_decision}; the
 * operator is the one who converts it (BOYA-54). §6's "AUTO is not auto-send" is the same sentence
 * from the other end: nothing here sends anything.
 *
 * <p>RECAPTURE is the one decision that moves the request somewhere else, because it is the one that
 * needs the customer again. The once-only limit is not checked here — {@code QuoteRequest} owns it,
 * and §6 states it as a property of the request rather than of whoever is asking.
 *
 * <p>Everything is priced before it is judged, including the readings that turn out to want a
 * recapture. §6's first line needs no price, so the order could be reversed — but only by asking the
 * unusable-frame question here and the rest of §6 in the evaluator, which would put one rule in two
 * places to save one pure computation. The draft it leaves behind is never shown: the request sits in
 * RECAPTURE_REQUIRED, and the re-analysis supersedes the quote before anybody could.
 */
@Service
class AnalysisConclusionService implements ConcludeAnalysis {

	private static final Logger log = LoggerFactory.getLogger(AnalysisConclusionService.class);

	private final QuoteRequestRepository requests;
	private final RoomRepository rooms;
	private final RoomAnalysisRepository analyses;
	private final ReadCaptureState capture;
	private final GenerateQuote quotes;
	private final PriceBookRepository priceBooks;
	private final ReviewOutcomes outcomes;
	private final ConfidenceEvaluator evaluator = new ConfidenceEvaluator();

	AnalysisConclusionService(QuoteRequestRepository requests, RoomRepository rooms,
			RoomAnalysisRepository analyses, ReadCaptureState capture, GenerateQuote quotes,
			PriceBookRepository priceBooks, ReviewOutcomes outcomes) {
		this.requests = requests;
		this.rooms = rooms;
		this.analyses = analyses;
		this.capture = capture;
		this.quotes = quotes;
		this.priceBooks = priceBooks;
		this.outcomes = outcomes;
	}

	@Override
	@Transactional
	public void concludeIfComplete(UUID quoteRequestId) {
		QuoteRequest request = requests.findById(quoteRequestId)
				.orElseThrow(() -> new QuoteRequestNotFound(quoteRequestId.toString()));

		if (request.status() != QuoteStatus.ANALYZING) {
			// Nothing to conclude. A request reaches here once per analysed room, and a recapture puts it
			// back through PHOTOS_PENDING — asking a request that is not being analysed to finish being
			// analysed would be answered by the state machine with an exception, which is the wrong shape
			// for "this call was early".
			log.debug("request {} is {}, not concluding", quoteRequestId, request.status());
			return;
		}

		List<RoomAnalysis> found = analyses.findByQuoteRequest(quoteRequestId);
		Set<UUID> analysedRooms = found.stream().map(RoomAnalysis::roomId).collect(Collectors.toSet());
		List<UUID> confirmed = rooms.findByQuoteRequest(quoteRequestId).rooms().stream()
				.map(room -> room.id())
				.toList();

		if (confirmed.isEmpty() || !analysedRooms.containsAll(confirmed)) {
			// Still arriving. Pricing now would quote the home for the rooms that finished first.
			return;
		}

		Quote quote = quotes.generate(quoteRequestId);
		Review review = evaluator.evaluate(reviewOf(request, quote, found, quoteRequestId));

		log.info("request {} reviewed as {} ({} lines, {} TL): {}", quoteRequestId, review.decision(),
				quote.priced().lines().size(), quote.priced().total(), review.reasons());

		outcomes.record(quoteRequestId, review);
		requests.save(switch (review.decision()) {
			// Both of these land in the queue. The mark is what the operator sorts on, not the status.
			case AUTO, SURVEY -> request.analysisComplete();
			case RECAPTURE -> request.requestRecapture();
		});
	}

	/**
	 * §6's inputs, gathered from what has just been written.
	 *
	 * <p>The thresholds come from the version that priced the quote, read off the quote itself rather
	 * than looked up again: a decision has to be explainable against the same figures the price was
	 * (§4.5), and asking twice would let an activation land between the two questions.
	 *
	 * <p>The skim share comes off the quote's own line for the same reason §5.5's deductions are not
	 * recounted here — the engine already did that arithmetic, and a second copy of it would answer
	 * differently the first time one was corrected.
	 */
	private ReviewInput reviewOf(QuoteRequest request, Quote quote, List<RoomAnalysis> found,
			UUID quoteRequestId) {
		PriceBook book = priceBooks.findByVersionCode(quote.priceBookVersion())
				.orElseThrow(() -> new IllegalStateException(
						"the version that priced this quote is gone: " + quote.priceBookVersion()));

		BigDecimal skim = quote.priced().hasLine(ItemCode.SKIM_COAT)
				? quote.priced().line(ItemCode.SKIM_COAT).quantity()
				: BigDecimal.ZERO;

		return ReviewInput.builder()
				.analyses(found)
				.estimatedTotal(quote.priced().total())
				.averageJobValue(book.averageJobValue())
				.surveyAmountFactor(book.surveyAmountFactor())
				.skim(skim, quote.priced().totalWallSqm())
				.recaptureCount(request.recaptureCount())
				.roomsMissingFrames(incompleteAreas(quoteRequestId))
				.build();
	}

	/**
	 * Areas still short of a frame they were asked for.
	 *
	 * <p>Empty by construction on the ordinary path: {@code submit()} refuses a request whose capture is
	 * incomplete. What this catches is a frame deleted afterwards, or a recapture the customer started
	 * and never finished — §6 lists it as a risk finding rather than an error because the analysis is
	 * real, it is just about less of the room than it appears to be.
	 */
	private List<String> incompleteAreas(UUID quoteRequestId) {
		return capture.of(quoteRequestId).areas().stream()
				.filter(area -> !area.complete())
				.map(CaptureState.CaptureArea::label)
				.toList();
	}
}
