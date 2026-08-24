package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.PhotoNotFound;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * What the capture routes answer when the request cannot be carried out.
 *
 * <p>Scoped to {@link PhotoController}, for the reason {@code QuoteRequestErrorHandler} gives: 409 for
 * an {@code IllegalStateException} is defensible only where the one such exception a caller can
 * provoke is the state machine refusing.
 */
@RestControllerAdvice(assignableTypes = PhotoController.class)
class PhotoErrorHandler {

	@ExceptionHandler({PhotoNotFound.class, QuoteRequestNotFound.class})
	ProblemDetail notFound(RuntimeException missing) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setTitle("Fotoğraf bulunamadı");
		return problem;
	}

	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail refused(IllegalStateException refused) {
		// Two cases, both the same answer: a frame that already exists, and a request that is not at a
		// point where photographs mean anything. Either way the client's next move is to re-read the
		// request rather than to retry this call.
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, refused.getMessage());
		problem.setTitle("Bu kare şu anda çekilemez");
		return problem;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail notAMeasurement(IllegalArgumentException refused) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.getMessage());
		problem.setTitle("Girilen değer kabul edilmedi");
		return problem;
	}
}
