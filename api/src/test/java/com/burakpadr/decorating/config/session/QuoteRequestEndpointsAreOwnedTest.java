package com.burakpadr.decorating.config.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.TestcontainersConfiguration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every route scoped to one quote request must be checked against the session (§7, BOYA-24).
 *
 * <p>{@code SecurityConfig}'s public chain leaves {@code /api/**} open at the URL level, because
 * ownership is a fact about a row rather than a path. That is the right call and it has a cost: nothing
 * in the framework fails when a handler simply forgets to ask. This is what fails instead.
 *
 * <p>It reads the live handler mapping rather than a list somebody maintains, so a route added next
 * week is covered the day it is added — which is the point, because §7 lists nine of these routes and
 * they arrive with BOYA-25, BOYA-37 and BOYA-45, long after the mechanism was written and stopped being
 * something anybody thinks about.
 *
 * <p>§7's photo routes are scoped to a customer too, and name a photograph rather than a request
 * ({@code /api/photos/{id}}), so the resolver cannot compare the cookie to the path. They take a
 * {@link CustomerSession} instead and check the row themselves — but declaring the parameter is still
 * the step that cannot be skipped, so it is asked for here in the same way.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class QuoteRequestEndpointsAreOwnedTest {

	/** §7's anonymous routes that name a request. */
	private static final String SCOPED_PREFIX = "/api/quote-requests/{id}";

	/** §7's anonymous routes that name a photograph, which belongs to a request two joins away. */
	private static final String PHOTO_PREFIX = "/api/photos";

	// By name: the actuator publishes a RequestMappingHandlerMapping of its own, so by type there are two.
	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	@Test
	@DisplayName("a handler under /api/quote-requests/{id} cannot be written without the ownership check")
	void everyScopedHandlerTakesTheOwnedParameter() {
		List<String> unchecked = mappings.getHandlerMethods().entrySet().stream()
				.filter(entry -> entry.getKey().getPatternValues().stream()
						.anyMatch(pattern -> pattern.startsWith(SCOPED_PREFIX)))
				.filter(entry -> !takesOwnedRequest(entry.getValue()))
				.map(entry -> entry.getKey() + " → " + entry.getValue().getMethod().getName())
				.toList();

		assertThat(unchecked)
				.as("each of these reads or writes somebody's answers, phone number or photographs with "
						+ "nothing but a guessable id standing in the way — declare an OwnedQuoteRequest "
						+ "parameter and the resolver does the rest")
				.isEmpty();
	}

	@Test
	@DisplayName("a handler under /api/photos cannot be written without the session either")
	void everyPhotoHandlerTakesTheSession() {
		List<String> unchecked = mappings.getHandlerMethods().entrySet().stream()
				.filter(entry -> entry.getKey().getPatternValues().stream()
						.anyMatch(pattern -> pattern.startsWith(PHOTO_PREFIX)))
				.filter(entry -> !takesSession(entry.getValue()))
				.map(entry -> entry.getKey() + " → " + entry.getValue().getMethod().getName())
				.toList();

		assertThat(unchecked)
				.as("a photo id is a row, not a credential: without the session these routes would read, "
						+ "complete or delete any photograph in the system by id")
				.isEmpty();
	}

	private boolean takesOwnedRequest(HandlerMethod handler) {
		return takes(handler, OwnedQuoteRequest.class);
	}

	private boolean takesSession(HandlerMethod handler) {
		return takes(handler, CustomerSession.class) || takesOwnedRequest(handler);
	}

	private boolean takes(HandlerMethod handler, Class<?> parameter) {
		return Arrays.stream(handler.getMethod().getParameterTypes()).anyMatch(parameter::equals);
	}
}
