package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.PhotoUploadIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * A reserved frame: where to PUT it, and what to call it afterwards (§9).
 *
 * <p>The window is sent rather than an expiry timestamp. A phone's clock is not ours, and a client
 * comparing a server instant against a device clock that is four minutes out would either retry
 * constantly or trust a URL that is already dead.
 */
record UploadIntentResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID photoId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
				description = "PUT the JPEG here, directly. It does not pass through this API.")
		String uploadUrl,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresInSeconds) {

	static UploadIntentResponse of(PhotoUploadIntent intent) {
		return new UploadIntentResponse(
				intent.photo().id(),
				intent.upload().url().toString(),
				intent.upload().expiresIn().toSeconds());
	}
}
