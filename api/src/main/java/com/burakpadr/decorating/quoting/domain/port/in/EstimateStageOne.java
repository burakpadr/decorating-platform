package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.StageOneEstimate;
import java.util.UUID;

/**
 * Stage 1's instant range for a stored draft (§7's {@code POST /quote-requests/{id}/estimate}).
 *
 * <p>Separate from {@code CalculateQuote}, which the operator tool uses, because the two answer
 * different questions with the same engine: the operator is comparing a cost, and this is quoting a
 * customer. Same pricing path, different amount of truth on the way out (§1).
 */
public interface EstimateStageOne {

	/**
	 * Prices the draft's answers and records the range against it.
	 *
	 * @throws com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound if there is no such
	 *     request
	 * @throws IllegalStateException if the answers are not complete enough to price — a missing answer
	 *     is refused rather than defaulted, because a default is the engine answering a question nobody
	 *     asked
	 */
	StageOneEstimate estimate(UUID id);
}
