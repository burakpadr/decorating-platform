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
 * <p><b>What is not here yet.</b> Stage 1's answers — district, area, layout, furnishing, the door
 * count — are columns on {@code quote_request} and belong to the draft-saving work (BOYA-25). This
 * class carries identity and the state the transitions actually guard: the recapture counter, the
 * contact reason and the close outcome. Fields with no use case to fill them would be a guess about
 * their shape, and every one of them would have to be threaded through eleven transitions.
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

	private QuoteRequest(UUID id, QuoteStatus status, int recaptureCount,
			ContactReason contactReason, CloseOutcome closeOutcome) {
		if (id == null) {
			throw new IllegalArgumentException("a quote request needs an id");
		}
		this.id = id;
		this.status = status;
		this.recaptureCount = recaptureCount;
		this.contactReason = contactReason;
		this.closeOutcome = closeOutcome;
	}

	/** A new request, before the customer has confirmed anything. */
	public static QuoteRequest draft(UUID id) {
		return new QuoteRequest(id, QuoteStatus.DRAFT, 0, null, null);
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
			ContactReason contactReason, CloseOutcome closeOutcome) {
		if (status == null) {
			throw new IllegalArgumentException("a stored request has a status");
		}
		return new QuoteRequest(id, status, recaptureCount, contactReason, closeOutcome);
	}

	// -----------------------------------------------------------------------------------------------
	// §3's events
	// -----------------------------------------------------------------------------------------------

	/** The customer accepted the derived room list, so there is something to photograph. */
	public QuoteRequest confirmRoomList() {
		return moveTo(QuoteStatus.PHOTOS_PENDING, QuoteStatus.DRAFT);
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
		return new QuoteRequest(
				id, QuoteStatus.RECAPTURE_REQUIRED, recaptureCount + 1, contactReason, closeOutcome);
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
		return new QuoteRequest(id, QuoteStatus.AWAITING_CONTACT, recaptureCount, reason, null);
	}

	/** The operator made the call and knows how it went. */
	public QuoteRequest close(CloseOutcome outcome) {
		require(QuoteStatus.AWAITING_CONTACT);
		if (outcome == null) {
			throw new IllegalArgumentException("a closed request has an outcome");
		}
		return new QuoteRequest(id, QuoteStatus.CLOSED, recaptureCount, contactReason, outcome);
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

	private QuoteRequest terminate(CloseOutcome outcome) {
		if (!OPEN.contains(status)) {
			// A cancelled request that was already WON is a number the business cannot reconcile, and a
			// scheduler running over a closed row would do exactly that at scale.
			throw new IllegalStateException(
					"a request in " + status + " has already ended; it cannot end again as " + outcome);
		}
		return new QuoteRequest(id, QuoteStatus.CLOSED, recaptureCount, contactReason, outcome);
	}

	private QuoteRequest moveTo(QuoteStatus target, QuoteStatus... from) {
		require(from);
		return new QuoteRequest(id, target, recaptureCount, contactReason, closeOutcome);
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
