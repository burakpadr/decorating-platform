package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.ConsentNoticeChanged;
import com.burakpadr.decorating.quoting.domain.model.ConsentOutOfOrder;
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

	@ExceptionHandler(ConsentNoticeChanged.class)
	ProblemDetail noticeChanged(ConsentNoticeChanged stale) {
		// 409 with the version to show instead. The client's next move is to re-read the notice and ask
		// again, and it cannot work that out from a sentence — a grant against words that are no longer
		// published is the one thing §12's versioning exists to prevent.
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
		problem.setType(java.net.URI.create("urn:decorating:consent-notice-changed"));
		problem.setTitle("Onay metni güncellendi");
		problem.setProperty("currentVersion", stale.current());
		return problem;
	}

	@ExceptionHandler(ConsentOutOfOrder.class)
	ProblemDetail outOfOrder(ConsentOutOfOrder early) {
		// Its own handler so it does not inherit the sentence below, which is about answers being frozen
		// and would send the customer looking for a form they have not filled in wrongly.
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, early.getMessage());
		problem.setTitle("Önce çekilecek alanlar onaylanmalı");
		return problem;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail notAnAnswer(IllegalArgumentException refused) {
		// A value the domain will not accept — today, a number no SMS can reach. 400 with the domain's own
		// sentence, which deliberately does not echo what was sent: an error message is a log line waiting
		// to happen, and a phone number is not a thing to log.
		ProblemDetail problem =
				ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refused.getMessage());
		problem.setTitle("Girilen değer kabul edilmedi");
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
