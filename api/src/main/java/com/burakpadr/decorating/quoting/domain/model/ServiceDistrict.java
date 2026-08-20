package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * A served district and its price factor (§4.5), per price book version.
 *
 * <p>{@code displayName} is the one Turkish string in the pricing domain — it is what the customer
 * reads on the district page and in the quote, so §1's language rule keeps it here rather than
 * deriving it from the code.
 */
public record ServiceDistrict(
		String districtCode, String displayName, boolean active, BigDecimal districtFactor) {}
