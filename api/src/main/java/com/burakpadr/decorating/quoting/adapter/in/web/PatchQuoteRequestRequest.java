package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * More of §2.1's answers. Every field optional, because the form sends what the screen just collected.
 *
 * <p>Absent means unchanged; there is no way to un-answer a question. Going back and choosing
 * differently sends the new value, which is what the customer means by it.
 *
 * <p>Validation is on the shape of an answer, not on whether the set is complete: a half-answered draft
 * is the normal state of this resource for as long as somebody is filling it in. Whether there is enough
 * to price is asked later, by {@code StageOneAnswers.isPriceable()}.
 */
record PatchQuoteRequestRequest(
		@Schema(example = "KADIKOY") String districtCode,
		@DecimalMin(value = "1.00") @Digits(integer = 5, fraction = 2) BigDecimal area,
		AreaBasis areaBasis,
		Layout layout,
		QuoteScope scope,
		Furnishing furnishing,
		// A home with more than fifty doors to paint is a typo or a hotel, and either way not a stage 1
		// quote. Zero is allowed: it means the doors are not in scope.
		@Min(0) @Max(50) Integer doorCount,
		Boolean doorColourChange,
		WallCondition wallCondition) {

	StageOneAnswers toAnswers() {
		return new StageOneAnswers(districtCode, area, areaBasis, layout, scope, furnishing,
				doorCount, doorColourChange, wallCondition);
	}
}
