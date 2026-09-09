package com.burakpadr.decorating.quoting.domain.model;

import java.util.List;

/**
 * §6's decision and why (workflow §4.3).
 *
 * <p>The reasons travel with the decision because two screens need them and neither can re-derive
 * them: the operator's queue shows the warning flags at the top (workflow §5.1), and a customer whose
 * survey was triggered by a risk finding is shown a widened range <em>and the reason</em> rather than
 * the stage 1 range they already saw (§6). Re-deriving in both places would put one rule in three.
 *
 * <p>Turkish, because both readers are people who speak it — the operator on the review screen and,
 * for a survey, the customer. This is the same rule as vision {@code notes} (§1).
 */
public record Review(ReviewDecision decision, List<String> reasons) {

	public Review {
		if (decision == null) {
			throw new IllegalArgumentException("a review reaches a decision");
		}
		reasons = List.copyOf(reasons);
		if (decision != ReviewDecision.AUTO && reasons.isEmpty()) {
			// AUTO is the only decision that needs no explanation: nothing happened. Anything that stops
			// a quote has to say what stopped it, or the operator queue shows a flag with no flag on it.
			throw new IllegalArgumentException(decision + " has to say why");
		}
	}

	public static Review auto() {
		return new Review(ReviewDecision.AUTO, List.of());
	}
}
