package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.ConsentNotice;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The notice to show, and the version to send back with the decision (workflow §2.3).
 *
 * <p>The body travels with its version rather than the frontend holding the words in its copy file:
 * the text is what a grant refers to, so a screen that could render one version's words under another
 * version's name would defeat the record (decision 0018).
 */
record ConsentNoticeResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ConsentType type,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "v1",
				description = "Send this back with the decision; a stale one is refused")
		String textVersion,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
				description = "Markdown. Turkish, and the only copy in this API that is not in tr.json")
		String body) {

	static ConsentNoticeResponse of(ConsentNotice notice) {
		return new ConsentNoticeResponse(notice.type(), notice.version(), notice.body());
	}
}
