package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.Quote;
import java.util.UUID;

/**
 * Pricing a request from what the analysis found (§5.1, §5.5, workflow §4.2).
 *
 * <p>Every room, or none. A home priced with one room missing is a home quoted for less work than it
 * needs, and the shortfall is invisible: the total looks like every other total. So a request whose
 * rooms are not all analysed is refused rather than partially priced.
 *
 * <p>It produces a {@code DRAFT} and moves nothing. Whether the findings are trustworthy enough to
 * act on is §6's evaluator (BOYA-51), and whether the customer sees it is the operator's (BOYA-54) —
 * §6's AUTO still means PENDING_REVIEW.
 */
public interface GenerateQuote {

	Quote generate(UUID quoteRequestId);
}
