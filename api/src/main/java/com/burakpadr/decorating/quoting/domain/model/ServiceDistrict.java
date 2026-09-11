package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * One area the business works in, on one price book version (§4.5, §10).
 *
 * <p>The factor is what the business charges for working there, and it is deliberately not part of
 * what a customer is shown (BOYA-26): a list carrying it would tell every visitor which
 * neighbourhoods are priced up.
 *
 * <p>{@code active} rather than presence: a district that closes keeps its factor and its history, so
 * the quotes priced while it was open stay readable.
 */
public record ServiceDistrict(
		String districtCode, String displayName, boolean active, BigDecimal districtFactor) {

	/** Widest factor that is still a factor. Outside this a 1.2 has been typed as 12, or as 0.12. */
	private static final BigDecimal MIN_FACTOR = new BigDecimal("0.50");
	private static final BigDecimal MAX_FACTOR = new BigDecimal("3.00");

	/**
	 * Checked on the way in rather than in the constructor: this type is also how districts are read
	 * back, and a row already in the database has to stay loadable even if a rule tightens later —
	 * the same reason a stored quote request's status is trusted rather than re-validated (BOYA-23).
	 */
	public void validate() {
		if (districtCode == null || districtCode.isBlank()) {
			throw new IllegalArgumentException("ilçe kodu boş olamaz");
		}
		if (displayName == null || displayName.isBlank()) {
			// What the customer picks from the list; a blank one is an unselectable row (§7).
			throw new IllegalArgumentException(
					districtCode + " için görünen ad boş olamaz");
		}
		if (districtFactor == null || districtFactor.compareTo(MIN_FACTOR) < 0
				|| districtFactor.compareTo(MAX_FACTOR) > 0) {
			throw new IllegalArgumentException(districtCode + " için ilçe katsayısı " + MIN_FACTOR
					+ " ile " + MAX_FACTOR + " arasında olmalı: " + districtFactor);
		}
	}
}
