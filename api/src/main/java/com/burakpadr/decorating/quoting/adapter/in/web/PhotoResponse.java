package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One frame, as the capture screen tracks it (§2.4).
 *
 * <p>No storage key. It signs nothing on its own, but it is the one field that says where a stranger's
 * home is kept, and a client that never needs it is a client that should never be sent it.
 */
record PhotoResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID roomId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PhotoRole role,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean uploaded,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean lowQualityFlag) {

	static PhotoResponse of(Photo photo) {
		return new PhotoResponse(photo.id(), photo.roomId(), photo.role(), photo.isUploaded(),
				photo.lowQualityFlag());
	}
}
