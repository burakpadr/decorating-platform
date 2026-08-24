package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Which frame of which area the browser is about to take (§9).
 *
 * <p>The role is the client's to state and the key is not: the client says "this is the ceiling of the
 * kitchen" and the server decides where that lives. A client that could name the key could write over
 * another room's frame with a URL we signed for it.
 */
record UploadIntentRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
				description = "One of the areas this request confirmed at §2.2")
		@NotNull UUID roomId,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull PhotoRole role) {}
