package com.burakpadr.decorating.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.TestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * The operator realm over real HTTP, through the real filter chain and a real servlet container.
 *
 * <p>This exists because MockMvc and the running application disagreed. MockMvc reported 401 for an
 * unauthenticated request while the server returned <b>403 with a {@code WWW-Authenticate} header</b> —
 * a combination no browser acts on: it prompts for credentials on 401 and never on 403. The panel could
 * therefore never log in, and every test was green.
 *
 * <p>A plain JDK client on purpose. The assertion is about what a browser receives, so nothing in the
 * request path should be a test framework's idea of an HTTP call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"spring.security.user.name=operator", "spring.security.user.password=test-secret",
				"decorating.cors.allowed-origins=http://localhost:3000"})
@Import(TestcontainersConfiguration.class)
class OperatorRealmHttpTest {

	@LocalServerPort
	private int port;

	@Test
	@DisplayName("an unauthenticated operator request is challenged with 401, not refused with 403")
	void challengesRatherThanRefuses() throws Exception {
		HttpResponse<String> response = get(null);

		assertThat(response.statusCode())
				.as("403 tells a browser 'never mind, you may not' — there is no prompt and no way in")
				.isEqualTo(401);
		assertThat(response.headers().firstValue("WWW-Authenticate").orElse(""))
				.as("and the challenge has to name the scheme, or the browser has nothing to answer with")
				.startsWith("Basic");
	}

	@Test
	@DisplayName("the right credentials get through")
	void letsTheOperatorIn() throws Exception {
		HttpResponse<String> response = get("operator:test-secret");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("REAL-2026-01");
	}

	@Test
	@DisplayName("the wrong password does not")
	void keepsTheWrongPasswordOut() throws Exception {
		assertThat(get("operator:wrong").statusCode()).isEqualTo(401);
	}

	@Test
	@DisplayName("a browser on the configured origin is allowed to call the API with credentials")
	void allowsThePanelsOrigin() throws Exception {
		HttpResponse<String> preflight;
		try (HttpClient client = HttpClient.newHttpClient()) {
			preflight = client.send(HttpRequest
							.newBuilder(URI.create("http://localhost:" + port + "/api/op/price-books"))
							.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
							.header("Origin", "http://localhost:3000")
							.header("Access-Control-Request-Method", "GET")
							.header("Access-Control-Request-Headers", "authorization")
							.build(),
					HttpResponse.BodyHandlers.ofString());
		}

		assertThat(preflight.statusCode()).isEqualTo(200);
		assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin"))
				.as("in development the panel and the API are two ports; in production they are one origin")
				.contains("http://localhost:3000");
		assertThat(preflight.headers().firstValue("Access-Control-Allow-Credentials"))
				.as("the operator's credentials ride on the request, so the origin can never be a wildcard")
				.contains("true");
	}

	private HttpResponse<String> get(String credentials) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest
				.newBuilder(URI.create("http://localhost:" + port + "/api/op/price-books"))
				.GET();
		if (credentials != null) {
			request.header("Authorization", "Basic "
					+ Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
		}
		try (HttpClient client = HttpClient.newHttpClient()) {
			return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
		}
	}
}
