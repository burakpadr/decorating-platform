package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.QuoteStatus;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A draft as the browser sees it.
 *
 * <p>It answers with everything stored rather than echoing what was sent, so the client can tell the
 * difference between "saved" and "sent" — which is the whole point of the ticket: the server is the
 * record, not {@code localStorage}.
 *
 * <p>{@code priceable} is here so the form knows when the estimate button means anything, and does not
 * have to keep its own copy of which of §2.1's questions are required.
 */
record QuoteRequestResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuoteStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean priceable,
		String districtCode,
		BigDecimal area,
		AreaBasis areaBasis,
		Layout layout,
		QuoteScope scope,
		Furnishing furnishing,
		Integer doorCount,
		Boolean doorColourChange,
		WallCondition wallCondition) {

	static QuoteRequestResponse of(QuoteRequest request) {
		StageOneAnswers answers = request.answers();
		return new QuoteRequestResponse(
				request.id(),
				request.status(),
				answers.isPriceable(),
				answers.districtCode(),
				answers.areaInput(),
				answers.areaBasis(),
				answers.layout(),
				answers.scope(),
				answers.furnishing(),
				answers.doorCount(),
				answers.doorColourChange(),
				answers.wallCondition());
	}
}
