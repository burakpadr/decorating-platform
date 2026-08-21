package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.DistrictNotServed;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * What the draft endpoints answer when the request cannot be carried out.
 *
 * <p>Scoped to {@link QuoteRequestController} rather than global: 409 for an
 * {@code IllegalStateException} is only defensible where the one such exception a caller can provoke is
 * the state machine refusing to answer a request that is no longer a draft. Anywhere else it would turn
 * a bug into a plausible-looking client error.
 */
@RestControllerAdvice(assignableTypes = QuoteRequestController.class)
class QuoteRequestErrorHandler {

	@ExceptionHandler(QuoteRequestNotFound.class)
	ProblemDetail notFound(QuoteRequestNotFound missing) {
		// Reachable only with a valid session for an id that has since been deleted (§8's photo and
		// request retention), because the session check already refused every other id.
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setTitle("Talep bulunamadı");
		return problem;
	}

	@ExceptionHandler(DistrictNotServed.class)
	ProblemDetail notServed(DistrictNotServed refused) {
		// 422 rather than 400: the request is well formed and the answer is a real answer — it is the area
		// we cannot take. Workflow §8 turns this screen into the waitlist offer (BOYA-28), which is the
		// only way a visitor we cannot serve is ever heard from again, so the client has to be able to
		// recognise this case without matching on a Turkish sentence.
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
		problem.setType(java.net.URI.create("urn:decorating:district-not-served"));
		problem.setTitle("Bu ilçede henüz hizmet vermiyoruz");
		problem.setProperty("districtCode", refused.districtCode());
		return problem;
	}

	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail noLongerADraft(IllegalStateException refused) {
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, refused.getMessage());
		problem.setTitle("Cevaplar artık değiştirilemez");
		return problem;
	}
}
