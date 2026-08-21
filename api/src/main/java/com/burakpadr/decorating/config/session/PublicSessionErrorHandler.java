package com.burakpadr.decorating.config.session;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * What a caller is told when the session does not let it through (§7).
 *
 * <p>Both answers are deliberately thin. A 403 that explained whose request it was would confirm the
 * existence of a stranger's quote to anybody who guessed an id, and stage 1 has no login for them to be
 * told to use instead.
 */
@RestControllerAdvice
class PublicSessionErrorHandler {

	@ExceptionHandler(SessionRequired.class)
	ProblemDetail sessionRequired(SessionRequired refused) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, refused.getMessage());
		problem.setTitle("Oturum gerekiyor");
		return problem;
	}

	@ExceptionHandler(NotYourQuoteRequest.class)
	ProblemDetail notYours(NotYourQuoteRequest refused) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
		problem.setTitle("Bu talep bu oturuma ait değil");
		return problem;
	}
}
