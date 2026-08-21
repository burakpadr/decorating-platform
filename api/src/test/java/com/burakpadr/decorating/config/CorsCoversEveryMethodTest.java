package com.burakpadr.decorating.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import com.burakpadr.decorating.TestcontainersConfiguration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * A browser's preflight, for every method the API maps.
 *
 * <p>Two lists have to agree — the verbs the controllers use and the verbs
 * {@code setAllowedMethods} names — and nothing compared them. In development the web app is on :3000
 * and the API on :8080, so every call is cross-origin: a method missing from the CORS list is an endpoint
 * a browser cannot call at all, while every MockMvc test still passes because MockMvc does not preflight.
 * PATCH was missing, which is every write in stage 1's form, and it surfaced as a button stuck on
 * "Kaydediliyor…" with nothing in the log.
 *
 * <p>The methods are read from the live mappings rather than listed here, so an endpoint added with a
 * verb nobody has used before fails the build the day it appears rather than the day somebody opens a
 * browser.
 */
@SpringBootTest(properties = "decorating.cors.allowed-origins=http://localhost:3000")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CorsCoversEveryMethodTest {

	private static final String ORIGIN = "http://localhost:3000";

	@Autowired
	private MockMvc mvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	@Test
	@DisplayName("a browser is allowed to send every method the API maps")
	void everyMappedMethodSurvivesPreflight() throws Exception {
		Set<String> mapped = new TreeSet<>();
		mappings.getHandlerMethods().keySet().forEach(info ->
				info.getMethodsCondition().getMethods().forEach(method -> mapped.add(method.name())));
		assertThat(mapped).as("nothing to check would mean the mappings were not read").isNotEmpty();

		List<String> refused = new java.util.ArrayList<>();
		for (String method : mapped) {
			String allowed = mvc.perform(options("/api/quote-requests/x")
							.header(HttpHeaders.ORIGIN, ORIGIN)
							.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method))
					.andReturn().getResponse()
					.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
			if (allowed == null || !allowed.contains(method)) {
				refused.add(method);
			}
		}

		assertThat(refused)
				.as("mapped but not allowed through a preflight, so unreachable from any browser")
				.isEmpty();
	}
}
