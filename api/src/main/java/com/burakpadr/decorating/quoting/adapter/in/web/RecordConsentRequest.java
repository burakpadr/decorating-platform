package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * What the customer decided, and which notice they were looking at (workflow §2.3).
 *
 * <p>{@code textVersion} is echoed, not chosen: it is the version this screen was served, and sending
 * it back is how the server can tell that the words on the screen are still the words it publishes. A
 * version it no longer publishes is refused rather than quietly replaced.
 *
 * <p>{@code granted} may be false. A refusal is an answer and §12 keeps it.
 */
record RecordConsentRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PROCESSING")
		@NotNull ConsentType type,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull Boolean granted,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "v1",
				description = "The version served by GET /api/consent-notices/{type}")
		@NotBlank String textVersion) {}
