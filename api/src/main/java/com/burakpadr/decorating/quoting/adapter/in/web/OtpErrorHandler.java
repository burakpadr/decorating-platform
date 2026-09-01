package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.OtpLocked;
import com.burakpadr.decorating.quoting.domain.model.OtpRefused;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.model.TooManyOtpRequests;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * What verification answers when it will not proceed (workflow §3.1, §11).
 *
 * <p>Three different refusals with three different next moves, and the screen has to tell them apart:
 * try the code again, ask for a new one, or wait. A single 400 for all three would leave the customer
 * retyping digits at a code that has been locked.
 */
@RestControllerAdvice(assignableTypes = OtpController.class)
class OtpErrorHandler {

	@ExceptionHandler(OtpRefused.class)
	ProblemDetail refused(OtpRefused refused) {
		// 422 rather than 400: the request is well formed and the answer is a real answer. No detail
		// about which of the five reasons it was — a caller that could tell them apart could learn
		// whether a code exists and how old it is, which is most of what a guesser wants.
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		problem.setType(java.net.URI.create("urn:decorating:otp-refused"));
		problem.setTitle("Kod doğrulanamadı");
		return problem;
	}

	@ExceptionHandler(OtpLocked.class)
	ProblemDetail locked(OtpLocked locked) {
		// 423 Locked: the code is finished and no further attempt will help. The screen has to offer a
		// new one rather than another try, which it cannot know from a 422.
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.LOCKED);
		problem.setType(java.net.URI.create("urn:decorating:otp-locked"));
		problem.setTitle("Bu kod kilitlendi");
		return problem;
	}

	@ExceptionHandler(TooManyOtpRequests.class)
	ProblemDetail tooMany(TooManyOtpRequests refused) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
		problem.setType(java.net.URI.create("urn:decorating:otp-rate-limited"));
		problem.setTitle("Çok fazla kod istendi");
		problem.setProperty("retryAfterSeconds", refused.retryAfter().toSeconds());
		return problem;
	}

	@ExceptionHandler(QuoteRequestNotFound.class)
	ProblemDetail notFound(QuoteRequestNotFound missing) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setTitle("Talep bulunamadı");
		return problem;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail notANumber(IllegalArgumentException refused) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.getMessage());
		problem.setTitle("Girilen değer kabul edilmedi");
		return problem;
	}
}
