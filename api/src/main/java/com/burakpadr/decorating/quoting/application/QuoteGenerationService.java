package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms.ConfirmedRoom;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PricedQuote;
import com.burakpadr.decorating.quoting.domain.model.PricingInput;
import com.burakpadr.decorating.quoting.domain.model.PricingSource;
import com.burakpadr.decorating.quoting.domain.model.Quote;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomInput;
import com.burakpadr.decorating.quoting.domain.model.SetupIncomplete;
import com.burakpadr.decorating.quoting.domain.model.SetupStatus;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.SurfaceFinding;
import com.burakpadr.decorating.quoting.domain.port.in.GenerateQuote;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import com.burakpadr.decorating.quoting.domain.port.out.PricedWithVersion;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRepository;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomAnalysisRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import com.burakpadr.decorating.quoting.domain.service.PricingEngine;
import com.burakpadr.decorating.shared.Uuid7;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Findings into a price (§5.1, §5.5, workflow §4.2, BOYA-50).
 *
 * <p>The engine is the same engine and the input is the same shape — {@link PricingInput#declaredBy}
 * is where both stages meet, so the only things this stage brings of its own are the rooms it builds
 * from {@code surface_finding} and the {@code STAGE_2} source that §5.5 and §5.9 read. Everything the
 * customer declared arrives the way stage 1 sent it.
 *
 * <p><b>Every room or none.</b> A home priced with one room missing is quoted for less work than it
 * needs, and the shortfall does not look like anything: the total is a total. So a request whose rooms
 * are not all analysed is refused, which is also what makes this safe to call the moment the last
 * analysis lands.
 *
 * <p><b>Priced against the version that priced the estimate</b>, not against whatever is live now. The
 * customer agreed to carry on from a range, and a quote computed against a version activated in
 * between would answer a different question than the one they were shown — the same reason
 * {@code room} reads its required frames from the request's version (BOYA-37) and §4.5 asks a stored
 * figure to remember which version produced it.
 *
 * <p>It writes a {@code DRAFT} and moves nothing else. §6's AUTO still means PENDING_REVIEW, so the
 * decision about the findings (BOYA-51) and the decision to send (BOYA-54) both sit after this.
 */
@Service
class QuoteGenerationService implements GenerateQuote {

	private final QuoteRequestRepository requests;
	private final RoomRepository rooms;
	private final RoomAnalysisRepository analyses;
	private final PriceBookRepository priceBooks;
	private final PricedWithVersion pricedWith;
	private final QuoteRepository quotes;
	private final PricingEngine engine = new PricingEngine();

	QuoteGenerationService(QuoteRequestRepository requests, RoomRepository rooms,
			RoomAnalysisRepository analyses, PriceBookRepository priceBooks,
			PricedWithVersion pricedWith, QuoteRepository quotes) {
		this.requests = requests;
		this.rooms = rooms;
		this.analyses = analyses;
		this.priceBooks = priceBooks;
		this.pricedWith = pricedWith;
		this.quotes = quotes;
	}

	@Override
	@Transactional
	public Quote generate(UUID quoteRequestId) {
		QuoteRequest request = requests.findById(quoteRequestId)
				.orElseThrow(() -> new QuoteRequestNotFound(quoteRequestId.toString()));
		StageOneAnswers answers = request.answers();
		if (!answers.isPriceable()) {
			throw new IllegalStateException(
					"this request cannot be priced: §2.1's questions are not all answered");
		}

		PriceBook book = bookThatPricedTheEstimate(quoteRequestId);
		List<RoomInput> analysed = analysedRooms(quoteRequestId);

		PricingInput input = PricingInput.declaredBy(
				answers,
				book.netAreaOf(answers.areaInput(), answers.areaBasis()),
				answers.areaBasis() == com.burakpadr.decorating.quoting.domain.model.AreaBasis.GROSS,
				analysed,
				PricingSource.STAGE_2);

		PricedQuote priced = engine.price(input, book);
		Quote quote = Quote.drafted(Uuid7.generate(), quoteRequestId,
				quotes.nextRevision(quoteRequestId), priced);
		quotes.save(quote);
		return quote;
	}

	/**
	 * One {@link RoomInput} per confirmed area, in capture order, refusing the moment one is missing.
	 *
	 * <p>The room's <em>type</em> comes from {@code room.room_type} and not from the analysis: that is
	 * the customer's answer and §5.3's area weights price it. What the model thought it was looking at
	 * stays in {@code raw_response} as evidence (decision 0021).
	 */
	private List<RoomInput> analysedRooms(UUID quoteRequestId) {
		Map<UUID, RoomAnalysis> byRoom = analyses.findByQuoteRequest(quoteRequestId).stream()
				.collect(Collectors.toMap(RoomAnalysis::roomId, Function.identity(),
						(first, second) -> first, LinkedHashMap::new));

		List<ConfirmedRoom> confirmed = rooms.findByQuoteRequest(quoteRequestId).rooms();
		if (confirmed.isEmpty()) {
			throw new IllegalStateException("request " + quoteRequestId + " has no confirmed areas");
		}

		List<RoomInput> analysed = new ArrayList<>(confirmed.size());
		for (ConfirmedRoom room : confirmed) {
			RoomAnalysis analysis = byRoom.get(room.id());
			if (analysis == null) {
				// Named, because "could not price" sends somebody to read seven rows to find out which.
				throw new IllegalStateException("area " + room.label() + " (" + room.id()
						+ ") has no analysis yet: a home is priced whole or not at all");
			}
			analysed.add(RoomInput.analysed(
					room.type(),
					analysis.surfaces().stream().map(SurfaceFinding::toInput).toList(),
					analysis.doorCount(),
					analysis.windowCount(),
					analysis.radiatorCount(),
					analysis.downlightCount(),
					analysis.cornice(),
					analysis.ceiling()));
		}
		return analysed;
	}

	private PriceBook bookThatPricedTheEstimate(UUID quoteRequestId) {
		return pricedWith.pricedWith(quoteRequestId)
				.flatMap(priceBooks::findById)
				// No bound version means no stage 1 estimate was ever stored for this request, which the
				// customer flow cannot produce — it prices before it asks for photographs. The active
				// version is the honest fallback for anything that got here another way.
				.or(priceBooks::findActive)
				// Neither the version that priced the estimate nor an active one: there is nothing here to
				// price against, which on a fresh install is the whole state of the system (BOYA-69).
				.orElseThrow(() -> new SetupIncomplete(SetupStatus.of(Optional.empty())));
	}
}
