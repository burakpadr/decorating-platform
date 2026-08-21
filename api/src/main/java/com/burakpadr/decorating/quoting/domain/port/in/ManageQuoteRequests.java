package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import java.util.UUID;

/**
 * Creating and filling in a stage 1 draft (§7's first two anonymous routes, BOYA-25).
 *
 * <p>Two operations because the form has two moments: it starts, and then it is answered a few
 * questions at a time. Workflow §8 is the reason the second one exists at all — people abandon on a
 * desktop and continue on a phone, so an answer that only ever reached {@code localStorage} is an
 * answer the customer gives twice.
 */
public interface ManageQuoteRequests {

	/**
	 * One draft, as stored.
	 *
	 * @throws com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound if there is no such
	 *     request
	 */
	QuoteRequest find(UUID id);

	/** A new empty draft. The caller is responsible for handing back the session that owns it. */
	QuoteRequest createDraft();

	/**
	 * Merges more of §2.1's answers into a draft.
	 *
	 * @throws com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound if there is no such
	 *     request
	 * @throws IllegalStateException if it is no longer a draft — the answers are fixed once the room
	 *     list is confirmed
	 */
	QuoteRequest answer(UUID id, StageOneAnswers patch);
}
