package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.config.session.CustomerSession;
import com.burakpadr.decorating.quoting.domain.port.in.CapturePhotos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The capture flow's three calls (§7, §9, BOYA-40).
 *
 * <p>The photograph never appears in any of them. The browser asks for a URL, PUTs the bytes straight
 * to storage, and says so afterwards — which is why there are three routes for what looks like one
 * upload, and why none of them accepts a file.
 *
 * <p>These are §7's only anonymous routes that do not name a quote request in the path, so they take a
 * {@link CustomerSession} rather than an {@code OwnedQuoteRequest}: the id in the path is a
 * photograph, and whether it is this customer's photograph is two joins away. The service is what
 * checks it, every time, against the request the cookie names.
 */
@RestController
@RequestMapping("/api/photos")
class PhotoController {

	private final CapturePhotos photos;

	PhotoController(CapturePhotos photos) {
		this.photos = photos;
	}

	@PostMapping("/upload-intent")
	@Operation(summary = "Reserve a frame and get the URL to PUT it to")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "A presigned PUT, and the id to report it by"),
			@ApiResponse(responseCode = "400", description = "No room or no role", content = {}),
			@ApiResponse(responseCode = "401", description = "No session cookie", content = {}),
			@ApiResponse(responseCode = "403",
					description = "The room belongs to another quote request", content = {}),
			@ApiResponse(responseCode = "409",
					description = "This frame is already photographed, or the request is not collecting "
							+ "photographs", content = {})})
	ResponseEntity<UploadIntentResponse> intend(
			// Resolved from the cookie, and hidden from the document for the same reason
			// OwnedQuoteRequest is: springdoc would otherwise publish the resolver's type as a query
			// parameter the generated client would try to send.
			@Parameter(hidden = true) CustomerSession session,
			@Valid @RequestBody UploadIntentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(UploadIntentResponse.of(
				photos.intend(session.quoteRequestId(), request.roomId(), request.role())));
	}

	@PostMapping("/{id}/complete")
	@Operation(summary = "The photograph arrived")
	@Parameter(name = "id", in = ParameterIn.PATH, required = true,
			description = "From the upload intent", schema = @Schema(type = "string", format = "uuid"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The frame, now uploaded"),
			@ApiResponse(responseCode = "401", description = "No session cookie", content = {}),
			@ApiResponse(responseCode = "403",
					description = "The photograph belongs to another quote request", content = {}),
			@ApiResponse(responseCode = "404", description = "No such photograph", content = {}),
			@ApiResponse(responseCode = "409",
					description = "The request is not collecting photographs", content = {})})
	PhotoResponse complete(@Parameter(hidden = true) CustomerSession session,
			@PathVariable UUID id, @Valid @RequestBody CompleteUploadRequest request) {
		return PhotoResponse.of(photos.complete(session.quoteRequestId(), id, request.toFrame()));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Retake: forget the frame and remove the object")
	@Parameter(name = "id", in = ParameterIn.PATH, required = true,
			schema = @Schema(type = "string", format = "uuid"))
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Gone, object and row"),
			@ApiResponse(responseCode = "401", description = "No session cookie", content = {}),
			@ApiResponse(responseCode = "403",
					description = "The photograph belongs to another quote request", content = {}),
			@ApiResponse(responseCode = "404", description = "No such photograph", content = {})})
	ResponseEntity<Void> discard(@Parameter(hidden = true) CustomerSession session,
			@PathVariable UUID id) {
		photos.discard(session.quoteRequestId(), id);
		return ResponseEntity.noContent().build();
	}
}
