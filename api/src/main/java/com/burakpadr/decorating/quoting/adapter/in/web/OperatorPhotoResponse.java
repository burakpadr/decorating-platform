package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import io.swagger.v3.oas.annotations.media.Schema;

/** One photograph, readable for as long as the review screen needs it and no longer (§9). */
record OperatorPhotoResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String url,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expiresInSeconds) {

	static OperatorPhotoResponse of(PresignedUrl presigned) {
		return new OperatorPhotoResponse(presigned.url().toString(), presigned.expiresIn().toSeconds());
	}
}
