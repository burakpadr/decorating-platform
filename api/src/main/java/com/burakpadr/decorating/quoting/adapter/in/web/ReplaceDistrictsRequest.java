package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.ServiceDistrict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * Where this business works, as a whole list (BOYA-70, BOYA-71).
 *
 * <p>The whole list rather than one district at a time: this answers a single question, and a partial
 * update makes it possible to open an area while leaving another one open by accident. Closing one is
 * therefore a new version, like every other change to a price book.
 *
 * <p>The bounds are the domain's, not annotations here — a factor of 12 is a mistyped 1.2 and the
 * rule belongs with the figure it describes.
 */
record ReplaceDistrictsRequest(@NotEmpty List<District> districts) {

	record District(
			@NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "KADIKOY")
			String code,
			@NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Kadıköy")
			String displayName,
			@NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean active,
			@NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1.0500")
			BigDecimal factor) {}

	List<ServiceDistrict> toDomain() {
		return districts.stream()
				.map(d -> new ServiceDistrict(d.code(), d.displayName(), d.active(), d.factor()))
				.toList();
	}
}
