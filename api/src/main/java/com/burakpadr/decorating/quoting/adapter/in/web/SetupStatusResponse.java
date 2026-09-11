package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.MissingSetting;
import com.burakpadr.decorating.quoting.domain.model.SetupStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Whether this install can quote, and what it still needs (BOYA-69).
 *
 * <p>{@code complete} travels rather than being inferred from an empty list: the panel decides what to
 * render from this one field, and a client that reasons "no entries, so we are set up" is one refactor
 * away from treating a failed read the same way. ADR 0015 settled the same question for
 * {@code editable}.
 */
record SetupStatusResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "false") boolean complete,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MissingSetting> missing) {

	static SetupStatusResponse of(SetupStatus status) {
		return new SetupStatusResponse(status.complete(), status.missing());
	}
}
