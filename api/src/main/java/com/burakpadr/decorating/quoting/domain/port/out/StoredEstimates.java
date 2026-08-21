package com.burakpadr.decorating.quoting.domain.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Reading back the range a customer was shown (§4.2's {@code estimate_low}, {@code estimate_high}).
 *
 * <p>Read rather than recomputed, deliberately. The SMS has to carry the figure the customer saw on
 * screen; recomputing it would put a different number in the message the moment a price book version is
 * activated between the two, and the customer would be holding two prices for one job.
 */
public interface StoredEstimates {

	Optional<Range> find(UUID quoteRequestId);

	/** The stored pair, and how §13's template wants it written. */
	record Range(BigDecimal low, BigDecimal high) {

		/** Whole lira with thousands separators, the way the screen shows it. */
		public String formatted() {
			return money(low) + "-" + money(high) + " TL";
		}

		private static String money(BigDecimal amount) {
			return String.format(java.util.Locale.of("tr", "TR"), "%,.0f", amount);
		}
	}
}
