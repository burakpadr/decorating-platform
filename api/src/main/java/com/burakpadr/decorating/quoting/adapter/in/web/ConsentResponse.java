package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.Consent;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * The decision as it was recorded (workflow §2.3).
 *
 * <p>The row's id is not returned. Nothing the client can do with a consent needs one — a change of
 * mind is a new decision, not an edit to this one — and an id it never sees is an id it cannot be
 * tricked into sending somewhere.
 */
record ConsentResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ConsentType type,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean granted,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "v1") String textVersion,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt) {

	static ConsentResponse of(Consent consent) {
		return new ConsentResponse(consent.type(), consent.granted(), consent.textVersion(),
				consent.recordedAt());
	}
}
