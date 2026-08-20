package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.DuplicateVersionCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionLocked;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionNotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the domain's refusals into the statuses the panel can act on.
 *
 * <p>Without this they would all be 500s, and a duplicate version code is not a server error — it is
 * an operator who needs to be told the code is taken. The message travels: an operator who cannot see
 * which code collided will pick another at random.
 */
@RestControllerAdvice(assignableTypes = {PriceBookController.class, QuoteCalculationController.class})
class OperatorErrorHandler {

	@ExceptionHandler(PriceBookVersionNotFound.class)
	ProblemDetail notFound(PriceBookVersionNotFound exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(DuplicateVersionCode.class)
	ProblemDetail conflict(DuplicateVersionCode exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	/**
	 * A version that has priced something. A conflict rather than a bad request: the panel asked for
	 * something reasonable, and the answer is "copy it and edit the copy".
	 */
	@ExceptionHandler(PriceBookVersionLocked.class)
	ProblemDetail locked(PriceBookVersionLocked exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
	}

	/**
	 * A refused argument the use case guards rather than the DTO — a percent outside its bounds, a
	 * version code that cannot fit. The operator asked for something impossible, not something broken.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail badRequest(IllegalArgumentException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
	}
}
