package com.burakpadr.decorating.quoting.domain.model;

import java.util.Set;
import java.util.UUID;

/**
 * One quote request, and the only thing allowed to move it (§3).
 *
 * <p>§3 finishes with the two sentences this class exists for: "Enforce transitions in the domain
 * model. Do not let adapters set status directly." So there is no status argument anywhere except
 * {@link #rehydrate}, and every other way forward is an event named after what happened —
 * {@code approve()}, not {@code toQuoteSent()}. Two states reach {@code QUOTE_SENT} for two different
 * reasons; a method named after the destination could not tell them apart, and the audit trail would
 * lose the distinction that matters.
 *
 * <p><b>Immutable.</b> A transition returns a new instance and leaves its source alone. An aggregate
 * that moved in place would be ahead of its row the moment a save failed, and the in-memory object
 * would keep answering questions with a status the database never accepted.
 *
 * <p>Stage 1's answers live in {@link StageOneAnswers} rather than as nine more fields here: they
 * accumulate across three screens and the state machine does not care what they say, so the two change
 * for different reasons and at different times. Every transition carries them forward untouched.
 */
public final class QuoteRequest {

	/** §3's terminal transitions reach every state but the one they produce. */
	private static final Set<QuoteStatus> OPEN = Set.of(
			QuoteStatus.DRAFT,
			QuoteStatus.PHOTOS_PENDING,
			QuoteStatus.ANALYZING,
			QuoteStatus.RECAPTURE_REQUIRED,
			QuoteStatus.PENDING_REVIEW,
			QuoteStatus.SURVEY_REQUIRED,
			QuoteStatus.QUOTE_SENT,
			QuoteStatus.AWAITING_CONTACT);

	/** §6: "Recapture is requested once only." */
	private static final int RECAPTURE_LIMIT = 1;

	private final UUID id;
	private final QuoteStatus status;
	private final int recaptureCount;
	private final ContactReason contactReason;
	private final CloseOutcome closeOutcome;
	private final StageOneAnswers answers;

	private QuoteRequest(UUID id, QuoteStatus status, int recaptureCount,
			ContactReason contactReason, CloseOutcome closeOutcome, StageOneAnswers answers) {
		if (id == null) {
			throw new IllegalArgumentException("a quote request needs an id");
		}
		this.id = id;
		this.status = status;
		this.recaptureCount = recaptureCount;
		this.contactReason = contactReason;
		this.closeOutcome = closeOutcome;
		this.answers = answers == null ? StageOneAnswers.empty() : answers;
	}

	/** A new request, before the customer has answered or confirmed anything. */
	public static QuoteRequest draft(UUID id) {
		return new QuoteRequest(id, QuoteStatus.DRAFT, 0, null, null, StageOneAnswers.empty());
	}

	/**
	 * Rebuilds a request from its row, without checking how it got there.
	 *
	 * <p>The one way in that takes a status, and it exists because a request outlives the process that
	 * created it. It trusts the row precisely because the row was written by the transitions below —
	 * re-validating a stored state would mean re-deciding history, and a rule added later would make old
	 * rows unloadable. {@code ArchitectureRulesTest} keeps every caller but the persistence adapter out.
	 */
	public static QuoteRequest rehydrate(UUID id, QuoteStatus status, int recaptureCount,
			ContactReason contactReason, CloseOutcome closeOutcome, StageOneAnswers answers) {
		if (status == null) {
			throw new IllegalArgumentException("a stored request has a status");
		}
		return new QuoteRequest(id, status, recaptureCount, contactReason, closeOutcome, answers);
	}

	// -----------------------------------------------------------------------------------------------
	// §3's events
	// -----------------------------------------------------------------------------------------------

	/**
	 * More of §2.1's answers, merged over the ones already given (BOYA-25).
	 *
	 * <p>DRAFT only. The customer confirms a room list derived from these answers, photographs the rooms
	 * that list named, and is quoted on both — so an answer changed after the draft is confirmed
	 * invalidates everything downstream of it, and nothing on the request would show it had happened.
	 * Correcting an answer later is a new request, which is also what the customer means by it.
	 */
	public QuoteRequest answer(StageOneAnswers patch) {
		require(QuoteStatus.DRAFT);
		return new QuoteRequest(
				id, status, recaptureCount, contactReason, closeOutcome, answers.mergedWith(patch));
	}

	/** The customer accepted the derived room list, so there is something to photograph. */
	public QuoteRequest confirmRoomList() {
		return moveTo(QuoteStatus.PHOTOS_PENDING, QuoteStatus.DRAFT);
	}

	/**
	 * Whether §3 has this request at a point where photographs are being collected.
	 *
	 * <p>A question rather than a transition, because taking a photograph does not move the request:
	 * the arrow out of {@code PHOTOS_PENDING} is {@link #submit()}, and it needs every required frame
	 * plus a verified phone. {@code RECAPTURE_REQUIRED} is here for the same reason it exists — §6 asks
	 * the customer for better frames, and refusing them would make that request unanswerable.
	 */
	public boolean acceptsPhotographs() {
		return status == QuoteStatus.PHOTOS_PENDING || status == QuoteStatus.RECAPTURE_REQUIRED;
	}

	/** Every required photo is in and the phone is verified (§3's guard on this arrow). */
	public QuoteRequest submit() {
		return moveTo(QuoteStatus.ANALYZING, QuoteStatus.PHOTOS_PENDING);
	}

	/**
	 * The photos cannot be analysed and the customer is asked for better ones — once, ever.
	 *
	 * <p>The limit lives here rather than in the caller because §6 states it as a property of the
	 * request, and a counter checked by whichever service happens to remember is a counter that will be
	 * forgotten by the second service.
	 */
	public QuoteRequest requestRecapture() {
		require(QuoteStatus.ANALYZING);
		if (recaptureCount >= RECAPTURE_LIMIT) {
			throw new IllegalStateException(
					"recapture has already been requested once; a second failure goes to the operator");
		}
		return new QuoteRequest(id, QuoteStatus.RECAPTURE_REQUIRED, recaptureCount + 1, contactReason,
				closeOutcome, answers);
	}

	/** The customer uploaded the replacements. */
	public QuoteRequest resubmitPhotos() {
		return moveTo(QuoteStatus.PHOTOS_PENDING, QuoteStatus.RECAPTURE_REQUIRED);
	}

	/** The analysis finished — whatever it found, the operator looks next (§6: AUTO is not auto-send). */
	public QuoteRequest analysisComplete() {
		return moveTo(QuoteStatus.PENDING_REVIEW, QuoteStatus.ANALYZING);
	}

	/** Low confidence or a risk finding: somebody has to go and look (§6). */
	public QuoteRequest requireSurvey() {
		return moveTo(QuoteStatus.SURVEY_REQUIRED, QuoteStatus.PENDING_REVIEW);
	}

	/**
	 * The operator sends the customer a figure.
	 *
	 * <p>Reached from review and from survey alike: §3 draws SURVEY_REQUIRED as a detour, not a dead end.
	 * What differs is which range is shown (§6), and that is the sending code's business, not the state
	 * machine's.
	 */
	public QuoteRequest approve() {
		return moveTo(QuoteStatus.QUOTE_SENT, QuoteStatus.PENDING_REVIEW, QuoteStatus.SURVEY_REQUIRED);
	}

	/** The customer accepted, asked for a survey, or has a question — all three want a phone call. */
	public QuoteRequest contact(ContactReason reason) {
		require(QuoteStatus.QUOTE_SENT);
		if (reason == null) {
			throw new IllegalArgumentException("a call-back has a reason");
		}
		return new QuoteRequest(id, QuoteStatus.AWAITING_CONTACT, recaptureCount, reason, null, answers);
	}

	/** The operator made the call and knows how it went. */
	public QuoteRequest close(CloseOutcome outcome) {
		require(QuoteStatus.AWAITING_CONTACT);
		if (outcome == null) {
			throw new IllegalArgumentException("a closed request has an outcome");
		}
		return new QuoteRequest(id, QuoteStatus.CLOSED, recaptureCount, contactReason, outcome, answers);
	}

	/** The operator stops it, from wherever it is. */
	public QuoteRequest cancel() {
		return terminate(CloseOutcome.CANCELLED);
	}

	/** The scheduler stops it: §8's validity window ran out. */
	public QuoteRequest expire() {
		return terminate(CloseOutcome.EXPIRED);
	}

	// -----------------------------------------------------------------------------------------------

	public UUID id() {
		return id;
	}

	public QuoteStatus status() {
		return status;
	}

	public int recaptureCount() {
		return recaptureCount;
	}

	public ContactReason contactReason() {
		return contactReason;
	}

	public CloseOutcome closeOutcome() {
		return closeOutcome;
	}

	public StageOneAnswers answers() {
		return answers;
	}

	private QuoteRequest terminate(CloseOutcome outcome) {
		if (!OPEN.contains(status)) {
			// A cancelled request that was already WON is a number the business cannot reconcile, and a
			// scheduler running over a closed row would do exactly that at scale.
			throw new IllegalStateException(
					"a request in " + status + " has already ended; it cannot end again as " + outcome);
		}
		return new QuoteRequest(id, QuoteStatus.CLOSED, recaptureCount, contactReason, outcome, answers);
	}

	private QuoteRequest moveTo(QuoteStatus target, QuoteStatus... from) {
		require(from);
		return new QuoteRequest(id, target, recaptureCount, contactReason, closeOutcome, answers);
	}

	private void require(QuoteStatus... allowed) {
		for (QuoteStatus candidate : allowed) {
			if (status == candidate) {
				return;
			}
		}
		throw new IllegalStateException(
				"a request in " + status + " cannot take this step; §3 draws it only from "
						+ String.join(" or ", java.util.Arrays.stream(allowed).map(Enum::name).toList()));
	}
}
