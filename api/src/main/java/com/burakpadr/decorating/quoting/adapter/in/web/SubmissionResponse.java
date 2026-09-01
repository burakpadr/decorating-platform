package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.port.in.SubmitQuoteRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * What the waiting screen is built from (workflow §3.2).
 *
 * <p>An instant, not a sentence. §8 computes the promise against working hours on the server — the
 * client has no idea when the business opens — but rendering it is the client's, because "yarın sabah
 * 10:00'a kadar" is copy and all copy lives in {@code tr.json}.
 */
record SubmissionResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "ANALYZING") String status,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
				description = "When the customer is told to expect an answer, computed against §8's hours")
		Instant respondBy) {

	static SubmissionResponse of(SubmitQuoteRequest.Submission submission) {
		return new SubmissionResponse("ANALYZING", submission.respondBy());
	}
}
