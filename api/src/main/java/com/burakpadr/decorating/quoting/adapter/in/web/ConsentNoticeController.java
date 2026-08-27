package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import com.burakpadr.decorating.quoting.domain.port.in.ReadConsentNotice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The data notice §2.3 puts on the capture guidance screen (BOYA-39).
 *
 * <p>Outside {@code /api/quote-requests} and outside the session, because it is a public statement of
 * what the business does with photographs rather than anything about one request. Requiring a cookie to
 * read it would also mean the text could not be linked to from anywhere else, which is the opposite of
 * what a privacy notice is for.
 */
@RestController
@RequestMapping("/api/consent-notices")
@Tag(name = "Consent", description = "The versioned data notice (workflow §2.3)")
class ConsentNoticeController {

	private final ReadConsentNotice notices;

	ConsentNoticeController(ReadConsentNotice notices) {
		this.notices = notices;
	}

	@GetMapping("/{type}")
	@Operation(summary = "The notice currently published for this consent type")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The text and the version that names it"),
			@ApiResponse(responseCode = "404", description = "No notice is published for that type yet",
					content = {})})
	ConsentNoticeResponse notice(@PathVariable ConsentType type) {
		return ConsentNoticeResponse.of(notices.current(type));
	}
}
