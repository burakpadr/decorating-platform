package com.burakpadr.decorating.quoting.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * What {@link ConsentNoticeController} answers for a type that has no notice.
 *
 * <p>404 rather than 500: {@code RETENTION_FOR_IMPROVEMENT} is a value the schema accepts and §16 has
 * not written the words for yet, so asking for it is a question with no answer rather than a fault.
 */
@RestControllerAdvice(assignableTypes = ConsentNoticeController.class)
class ConsentNoticeErrorHandler {

	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail noNoticeYet(IllegalStateException missing) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
		problem.setTitle("Bu onay metni henüz yayımlanmadı");
		return problem;
	}
}
