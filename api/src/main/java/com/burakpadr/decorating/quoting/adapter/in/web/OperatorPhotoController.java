package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.PhotoNotFound;
import com.burakpadr.decorating.quoting.domain.port.in.ReviewPhotos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading a photograph back, in the operator realm (§9, BOYA-40).
 *
 * <p>§7 does not list this route and the review screen cannot exist without it: the bucket is private,
 * every read is presigned, and the operator's browser needs a URL from somewhere. Same reasoning as
 * {@code docs/decisions/0015}, and the same care — it lives under {@code /api/op} so the operator chain
 * guards it, because a customer-reachable version of this endpoint would be a way to read any
 * photograph in the system by id.
 */
@RestController
@RequestMapping("/api/op/photos")
class OperatorPhotoController {

	private final ReviewPhotos photos;

	OperatorPhotoController(ReviewPhotos photos) {
		this.photos = photos;
	}

	@GetMapping("/{id}")
	@Operation(summary = "A short-lived URL for one photograph")
	@Parameter(name = "id", in = ParameterIn.PATH, required = true,
			schema = @Schema(type = "string", format = "uuid"))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The presigned read and its window"),
			@ApiResponse(responseCode = "401", description = "Not an operator", content = {}),
			@ApiResponse(responseCode = "404", description = "No such photograph", content = {})})
	OperatorPhotoResponse read(@PathVariable UUID id) {
		return OperatorPhotoResponse.of(photos.readable(id));
	}

	@ExceptionHandler(PhotoNotFound.class)
	ProblemDetail notFound(PhotoNotFound missing) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setTitle("Fotoğraf bulunamadı");
		return problem;
	}
}
