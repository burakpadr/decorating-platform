package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.config.session.AnonymousSessionCookie;
import com.burakpadr.decorating.config.session.OwnedQuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.in.ManageQuoteRequests;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starting and filling in a stage 1 draft (§7, BOYA-25).
 *
 * <p>Anonymous: §4.1 forbids a customer row before OTP verification, so the only thing tying a browser
 * to its draft is the session cookie this controller issues (BOYA-24). The POST is the one route here
 * without one — it is what hands it out.
 *
 * <p>Workflow §8 is why PATCH exists rather than a single submit at the end: people fill in two screens
 * on a laptop and finish on a phone, and an answer that lived only in the browser is an answer they get
 * asked for twice.
 */
@RestController
@RequestMapping("/api/quote-requests")
class QuoteRequestController {

	private final ManageQuoteRequests requests;
	private final EstimateStageOne estimates;
	private final AnonymousSessionCookie session;

	QuoteRequestController(ManageQuoteRequests requests, EstimateStageOne estimates,
			AnonymousSessionCookie session) {
		this.requests = requests;
		this.estimates = estimates;
		this.session = session;
	}

	@PostMapping
	@Operation(summary = "Start a draft and take ownership of it")
	@ApiResponses({
			@ApiResponse(responseCode = "201",
					description = "Created. Carries the session cookie every later call needs.")})
	ResponseEntity<QuoteRequestResponse> create() {
		QuoteRequest draft = requests.createDraft();
		// The cookie is the response, as much as the body is: a caller that stored the id and dropped the
		// Set-Cookie header would hold an id it can never use again.
		return ResponseEntity.status(HttpStatus.CREATED)
				.header(HttpHeaders.SET_COOKIE, session.asCookie(draft.id()).toString())
				.body(QuoteRequestResponse.of(draft));
	}

	@PatchMapping("/{id}")
	@Operation(summary = "Answer more of the eight questions")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The draft as stored, after the merge"),
			@ApiResponse(responseCode = "401", description = "No session cookie", content = {}),
			@ApiResponse(responseCode = "403", description = "The session owns a different draft",
					content = {}),
			@ApiResponse(responseCode = "409",
					description = "No longer a draft: the answers are fixed once the room list is "
							+ "confirmed", content = {})})
	QuoteRequestResponse answer(
			// Not a @PathVariable. The type is only obtainable from the resolver that has already matched
			// the cookie against this path's id (BOYA-24), so the check cannot be forgotten here.
			OwnedQuoteRequest owned,
			@Valid @RequestBody PatchQuoteRequestRequest request) {
		return QuoteRequestResponse.of(requests.answer(owned.id(), request.toAnswers()));
	}

	@PostMapping("/{id}/estimate")
	@Operation(summary = "The instant range for the answers given so far")
	@ApiResponses({
			@ApiResponse(responseCode = "200",
					description = "The range, the areas it assumed, and how wide the band is"),
			@ApiResponse(responseCode = "401", description = "No session cookie", content = {}),
			@ApiResponse(responseCode = "403", description = "The session owns a different draft",
					content = {}),
			@ApiResponse(responseCode = "409",
					description = "Not all of §2.1's questions are answered yet", content = {})})
	StageOneEstimateResponse estimate(OwnedQuoteRequest owned) {
		return StageOneEstimateResponse.of(estimates.estimate(owned.id()));
	}
}
