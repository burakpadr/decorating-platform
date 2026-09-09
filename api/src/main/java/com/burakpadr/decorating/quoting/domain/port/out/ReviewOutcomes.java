package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.Review;
import java.util.UUID;

/**
 * Records what §6 decided about an analysis and why ({@code quote_request.review_decision},
 * {@code review_reasons}).
 *
 * <p>Its own port rather than another field on {@code QuoteRequest}: the aggregate carries what its
 * transitions protect, and this is the output of reading an analysis rather than part of the state
 * machine. §3 has no arrow that depends on it.
 *
 * <p>Stored rather than derived because two screens read it and neither can recompute it. The
 * operator's queue shows the flags (workflow §5.1); a customer sent to a survey by a risk finding is
 * shown a widened range and the reason (§6). A third copy of §6's rule is a third answer.
 */
public interface ReviewOutcomes {

	void record(UUID quoteRequestId, Review review);
}
