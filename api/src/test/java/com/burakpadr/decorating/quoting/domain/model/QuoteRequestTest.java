package com.burakpadr.decorating.quoting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The quote request's life (§3), which is the backbone of stage 1 — every screen in Artış 2 hangs off
 * one of these states.
 *
 * <p>§3 ends with two sentences that decide the shape of this test: "Enforce transitions in the domain
 * model. Do not let adapters set status directly." So the interesting assertions are not that the happy
 * path works — it is one line of arrows — but that every other path is refused, and that there is no way
 * in from the outside except the named events.
 *
 * <p>Transitions are named after what happened, not after where it lands. {@code approve()} rather than
 * {@code toQuoteSent()}: the same destination is reached from two states for two different reasons, and
 * a method named after the destination cannot say which.
 */
class QuoteRequestTest {

	private static QuoteRequest fresh() {
		return QuoteRequest.draft(UUID.randomUUID());
	}

	/** The whole diagram, in order, with nothing branching. */
	private static QuoteRequest at(QuoteStatus status) {
		QuoteRequest request = fresh();
		if (status == QuoteStatus.DRAFT) {
			return request;
		}
		request = request.confirmRoomList();
		if (status == QuoteStatus.PHOTOS_PENDING) {
			return request;
		}
		request = request.submit();
		if (status == QuoteStatus.ANALYZING) {
			return request;
		}
		if (status == QuoteStatus.RECAPTURE_REQUIRED) {
			return request.requestRecapture();
		}
		request = request.analysisComplete();
		if (status == QuoteStatus.PENDING_REVIEW) {
			return request;
		}
		if (status == QuoteStatus.SURVEY_REQUIRED) {
			return request.requireSurvey();
		}
		request = request.approve();
		if (status == QuoteStatus.QUOTE_SENT) {
			return request;
		}
		request = request.contact(ContactReason.ACCEPTED);
		if (status == QuoteStatus.AWAITING_CONTACT) {
			return request;
		}
		return request.close(CloseOutcome.WON);
	}

	// =============================================================================================
	// The path §3 draws
	// =============================================================================================

	@Test
	@DisplayName("§3's happy path, arrow by arrow")
	void walksTheHappyPath() {
		QuoteRequest request = fresh();

		assertThat(request.status()).isEqualTo(QuoteStatus.DRAFT);
		assertThat(request.confirmRoomList().status()).isEqualTo(QuoteStatus.PHOTOS_PENDING);
		assertThat(at(QuoteStatus.ANALYZING).status()).isEqualTo(QuoteStatus.ANALYZING);
		assertThat(at(QuoteStatus.PENDING_REVIEW).status()).isEqualTo(QuoteStatus.PENDING_REVIEW);
		assertThat(at(QuoteStatus.QUOTE_SENT).status()).isEqualTo(QuoteStatus.QUOTE_SENT);
		assertThat(at(QuoteStatus.AWAITING_CONTACT).status()).isEqualTo(QuoteStatus.AWAITING_CONTACT);
		assertThat(at(QuoteStatus.CLOSED).status()).isEqualTo(QuoteStatus.CLOSED);
	}

	@Test
	@DisplayName("a transition leaves the request it was called on untouched")
	void transitionsDoNotMutateTheirSource() {
		QuoteRequest draft = fresh();

		QuoteRequest next = draft.confirmRoomList();

		assertThat(draft.status())
				.as("an aggregate that mutated in place would let a failed save leave the object ahead of "
						+ "the row it came from")
				.isEqualTo(QuoteStatus.DRAFT);
		assertThat(next.id()).isEqualTo(draft.id());
	}

	// =============================================================================================
	// Recapture: the branch with a memory
	// =============================================================================================

	@Test
	@DisplayName("a recapture sends the customer back to the photo screen")
	void aRecaptureReturnsToPhotos() {
		QuoteRequest recapture = at(QuoteStatus.RECAPTURE_REQUIRED);

		assertThat(recapture.status()).isEqualTo(QuoteStatus.RECAPTURE_REQUIRED);
		assertThat(recapture.recaptureCount()).isEqualTo(1);
		assertThat(recapture.resubmitPhotos().status()).isEqualTo(QuoteStatus.PHOTOS_PENDING);
	}

	@Test
	@DisplayName("§6: recapture is requested once only — a second failure goes to the operator")
	void recaptureIsRequestedOnceOnly() {
		QuoteRequest secondAnalysis = at(QuoteStatus.RECAPTURE_REQUIRED)
				.resubmitPhotos()
				.submit();

		assertThatThrownBy(secondAnalysis::requestRecapture)
				.as("asking twice is what §6 forbids, and the state machine is where that has to hold — "
						+ "not in whichever caller happens to check the counter")
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("recapture");
		assertThat(secondAnalysis.analysisComplete().status())
				.as("the second attempt goes to review however it turned out")
				.isEqualTo(QuoteStatus.PENDING_REVIEW);
	}

	// =============================================================================================
	// Survey
	// =============================================================================================

	@Test
	@DisplayName("a survey-required request still reaches the customer as a quote")
	void aSurveyStillEndsInAQuote() {
		QuoteRequest survey = at(QuoteStatus.SURVEY_REQUIRED);

		assertThat(survey.approve().status())
				.as("§6: SURVEY is not a dead end — the operator acts and the customer gets a figure")
				.isEqualTo(QuoteStatus.QUOTE_SENT);
	}

	// =============================================================================================
	// Closing
	// =============================================================================================

	@Test
	@DisplayName("the contact reason and the close outcome are both recorded, not implied")
	void reasonAndOutcomeAreRecorded() {
		QuoteRequest closed = at(QuoteStatus.QUOTE_SENT)
				.contact(ContactReason.SURVEY)
				.close(CloseOutcome.LOST);

		assertThat(closed.contactReason()).isEqualTo(ContactReason.SURVEY);
		assertThat(closed.closeOutcome()).isEqualTo(CloseOutcome.LOST);
	}

	@ParameterizedTest
	@EnumSource(value = QuoteStatus.class, names = "CLOSED", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("§3: an operator can cancel from any state, and a scheduler can expire it")
	void cancelAndExpireReachEveryState(QuoteStatus from) {
		assertThat(at(from).cancel().status()).isEqualTo(QuoteStatus.CLOSED);
		assertThat(at(from).cancel().closeOutcome()).isEqualTo(CloseOutcome.CANCELLED);
		assertThat(at(from).expire().status()).isEqualTo(QuoteStatus.CLOSED);
		assertThat(at(from).expire().closeOutcome()).isEqualTo(CloseOutcome.EXPIRED);
	}

	@Test
	@DisplayName("a closed request is closed: not cancellable, not expirable, not reopenable")
	void closedIsTerminal() {
		QuoteRequest closed = at(QuoteStatus.CLOSED);

		assertThat(illegalFrom(closed))
				.as("cancel and expire reach every other state, and this is the one they must not — a "
						+ "cancelled row that was already WON is a number the business cannot reconcile")
				.containsExactlyInAnyOrderElementsOf(EVENTS.keySet());
	}

	// =============================================================================================
	// The answers (BOYA-25)
	// =============================================================================================

	@Test
	@DisplayName("a fresh draft has answered nothing")
	void aDraftStartsEmpty() {
		assertThat(fresh().answers()).isEqualTo(StageOneAnswers.empty());
	}

	@Test
	@DisplayName("answering keeps the request in DRAFT and keeps what was answered before")
	void answeringAccumulates() {
		QuoteRequest answered = fresh()
				.answer(new StageOneAnswers("KADIKOY", null, null, null, null, null, null, null, null, null))
				.answer(new StageOneAnswers(null, null, null, Layout.THREE_PLUS_ONE, null, null, null,
						null, null, null));

		assertThat(answered.status()).isEqualTo(QuoteStatus.DRAFT);
		assertThat(answered.answers().districtCode()).isEqualTo("KADIKOY");
		assertThat(answered.answers().layout()).isEqualTo(Layout.THREE_PLUS_ONE);
	}

	@ParameterizedTest
	@EnumSource(value = QuoteStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
	@DisplayName("once the draft is confirmed the answers are fixed — every later state refuses them")
	void answersAreFixedOnceTheDraftIsConfirmed(QuoteStatus from) {
		// The customer confirms a room list derived from these answers, photographs the rooms that list
		// named, and gets a price built on both. An answer changed after that point silently invalidates
		// everything downstream of it, and nothing in the request would show that it had happened.
		assertThatThrownBy(() -> at(from).answer(
						new StageOneAnswers(null, null, null, Layout.STUDIO, null, null, null, null, null, null)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("a transition carries the answers forward untouched")
	void transitionsKeepTheAnswers() {
		StageOneAnswers answers = new StageOneAnswers("USKUDAR", new BigDecimal("92"), AreaBasis.NET,
				Layout.THREE_PLUS_ONE, QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true,
				WallCondition.MINOR, null);

		QuoteRequest confirmed = fresh().answer(answers).confirmRoomList();

		assertThat(confirmed.answers()).isEqualTo(answers);
		assertThat(confirmed.status()).isEqualTo(QuoteStatus.PHOTOS_PENDING);
	}

	@Test
	@DisplayName("rehydration brings the answers back with the state")
	void rehydrationRestoresTheAnswers() {
		StageOneAnswers answers = new StageOneAnswers("KADIKOY", null, null, Layout.STUDIO, null, null,
				null, null, null, null);

		QuoteRequest restored = QuoteRequest.rehydrate(
				UUID.randomUUID(), QuoteStatus.DRAFT, 0, null, null, answers);

		assertThat(restored.answers()).isEqualTo(answers);
	}

	// =============================================================================================
	// Everything the diagram does not draw
	// =============================================================================================

	@ParameterizedTest
	@EnumSource(QuoteStatus.class)
	@DisplayName("every event the diagram does not draw from a state is refused from that state")
	void everyUndrawnTransitionIsRefused(QuoteStatus from) {
		// The table below is §3 read as a list of (state, event) pairs that exist. Anything not in it must
		// throw — which is the half of a state machine that is easy to leave out, because the happy path
		// passes either way and an unguarded jump only shows up as a request in a state nobody can explain.
		List<String> allowed = switch (from) {
			case DRAFT -> List.of("confirmRoomList", "cancel", "expire");
			case PHOTOS_PENDING -> List.of("submit", "cancel", "expire");
			case ANALYZING -> List.of("analysisComplete", "requestRecapture", "cancel", "expire");
			case RECAPTURE_REQUIRED -> List.of("resubmitPhotos", "cancel", "expire");
			case PENDING_REVIEW -> List.of("approve", "requireSurvey", "cancel", "expire");
			case SURVEY_REQUIRED -> List.of("approve", "cancel", "expire");
			case QUOTE_SENT -> List.of("contact", "cancel", "expire");
			case AWAITING_CONTACT -> List.of("close", "cancel", "expire");
			case CLOSED -> List.of();
		};

		assertThat(illegalFrom(at(from)))
				.as("%s allows exactly %s", from, allowed)
				.containsExactlyInAnyOrderElementsOf(
						EVENTS.keySet().stream().filter(name -> !allowed.contains(name)).toList());
	}

	/** Every event, so a new one cannot be added without this test noticing it exists. */
	private static final java.util.Map<String, UnaryOperator<QuoteRequest>> EVENTS =
			new java.util.LinkedHashMap<>();

	static {
		EVENTS.put("confirmRoomList", QuoteRequest::confirmRoomList);
		EVENTS.put("submit", QuoteRequest::submit);
		EVENTS.put("requestRecapture", QuoteRequest::requestRecapture);
		EVENTS.put("resubmitPhotos", QuoteRequest::resubmitPhotos);
		EVENTS.put("analysisComplete", QuoteRequest::analysisComplete);
		EVENTS.put("requireSurvey", QuoteRequest::requireSurvey);
		EVENTS.put("approve", QuoteRequest::approve);
		EVENTS.put("contact", request -> request.contact(ContactReason.ACCEPTED));
		EVENTS.put("close", request -> request.close(CloseOutcome.WON));
		EVENTS.put("cancel", QuoteRequest::cancel);
		EVENTS.put("expire", QuoteRequest::expire);
	}

	/** The events that throw from this request, by name. */
	private static List<String> illegalFrom(QuoteRequest request) {
		return EVENTS.entrySet().stream()
				.filter(entry -> {
					try {
						entry.getValue().apply(request);
						return false;
					} catch (IllegalStateException refused) {
						return true;
					}
				})
				.map(java.util.Map.Entry::getKey)
				.toList();
	}

	@Test
	@DisplayName("the aggregate exposes no way to set a status: only the events, and rehydration")
	void statusCannotBeSetFromOutside() {
		// §3: "Do not let adapters set status directly." The persistence adapter still has to rebuild a
		// request from its row, so the way in exists — it is named rehydrate, it is the only one, and
		// ArchitectureRulesTest keeps every caller but the adapter out of it.
		List<String> setters = Arrays.stream(QuoteRequest.class.getMethods())
				.map(java.lang.reflect.Method::getName)
				.filter(name -> name.startsWith("setStatus") || name.equals("withStatus"))
				.toList();

		assertThat(setters).isEmpty();
		assertThat(QuoteStatus.valueOf("CLOSED")).isEqualTo(QuoteStatus.CLOSED);
	}

	@Test
	@DisplayName("rehydration trusts the row, because the row was written by these rules")
	void rehydrationRestoresAnyState() {
		UUID id = UUID.randomUUID();

		QuoteRequest restored = QuoteRequest.rehydrate(
				id, QuoteStatus.AWAITING_CONTACT, 1, ContactReason.QUESTION, null,
				StageOneAnswers.empty());

		assertThat(restored.status()).isEqualTo(QuoteStatus.AWAITING_CONTACT);
		assertThat(restored.recaptureCount())
				.as("a request that had its one recapture must not get another after a restart")
				.isEqualTo(1);
		assertThatThrownBy(restored::requestRecapture).isInstanceOf(IllegalStateException.class);
		assertThat(restored.close(CloseOutcome.WON).status()).isEqualTo(QuoteStatus.CLOSED);
	}

	@Test
	@DisplayName("a request needs an identity")
	void identityIsRequired() {
		Consumer<UUID> construct = id -> QuoteRequest.draft(id);

		assertThatThrownBy(() -> construct.accept(null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
