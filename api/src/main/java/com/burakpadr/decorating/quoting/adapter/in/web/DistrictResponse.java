package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.ServiceDistrict;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One district, as a customer sees it.
 *
 * <p>Code and name, and deliberately not {@code districtFactor}. The factor is what the business
 * charges for working in an area — it belongs with the cost, on the operator's side of §1's line. A list
 * that carried it would tell every visitor which neighbourhoods we price up.
 */
record DistrictResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "KADIKOY") String code,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Kadıköy") String name) {

	static DistrictResponse of(ServiceDistrict district) {
		return new DistrictResponse(district.districtCode(), district.displayName());
	}
}
