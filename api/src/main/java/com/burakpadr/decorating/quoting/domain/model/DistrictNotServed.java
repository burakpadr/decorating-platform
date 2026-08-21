package com.burakpadr.decorating.quoting.domain.model;

/**
 * The customer's district is not one the business works in (workflow §7 decision 1).
 *
 * <p>Its own type rather than a validation message, because it is not a mistake the customer made and
 * the answer to it is a different screen: §8 offers to tell them when the area opens, and that offer is
 * the only way a visitor who cannot be served is ever heard from again.
 *
 * <p>The code is carried so the client can name the district in that offer without having to remember
 * what it just sent.
 */
public class DistrictNotServed extends RuntimeException {

	private final String districtCode;

	public DistrictNotServed(String districtCode) {
		super("not a district we serve: " + districtCode);
		this.districtCode = districtCode;
	}

	public String districtCode() {
		return districtCode;
	}
}
