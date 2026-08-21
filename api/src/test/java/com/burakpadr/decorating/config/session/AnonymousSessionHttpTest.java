package com.burakpadr.decorating.config.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The session cookie over HTTP (§7, BOYA-24) — the acceptance criteria of the ticket, in order: a
 * request carrying another request's id is refused, and without the cookie PATCH does not work.
 *
 * <p>The endpoints under test are declared here rather than in the application. §7's
 * {@code /api/quote-requests} routes belong to the draft-saving work (BOYA-25); what has to be true
 * before they exist is that the mechanism refuses everything it should, and a controller written for
 * the test says exactly which paths are being asserted about.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnonymousSessionHttpTest {

	private static final UUID MINE = UUID.fromString("01930000-0000-7000-8000-00000000aaaa");
	private static final UUID SOMEBODY_ELSES = UUID.fromString("01930000-0000-7000-8000-00000000bbbb");

	@TestConfiguration
	static class Endpoints {

		@RestController
		static class Probe {

			@GetMapping("/api/quote-requests/{id}/probe")
			String read(OwnedQuoteRequest owned) {
				return owned.id().toString();
			}

			// Its own path: the real PATCH /api/quote-requests/{id} exists now (BOYA-25) and two handlers
			// on one mapping is an ambiguous mapping, not a test.
			@PatchMapping("/api/quote-requests/{id}/probe")
			String update(OwnedQuoteRequest owned) {
				return owned.id().toString();
			}
		}
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AnonymousSessionCookie cookie;

	@Test
	@DisplayName("the request that owns the session gets through")
	void ownerGetsThrough() throws Exception {
		mvc.perform(get("/api/quote-requests/{id}/probe", MINE).cookie(session(MINE)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("acceptance: a request carrying another request's id is refused")
	void anotherRequestsIdIsRefused() throws Exception {
		mvc.perform(get("/api/quote-requests/{id}/probe", SOMEBODY_ELSES).cookie(session(MINE)))
				.andExpect(status().isForbidden())
				// Nothing about whose it was: confirming that some other session owns an id would tell a
				// caller that guessed one that it exists.
				.andExpect(jsonPath("$.detail").doesNotExist());
	}

	@Test
	@DisplayName("a write needs the cookie as much as a read does")
	void writesNeedTheCookie() throws Exception {
		// The same assertion against the real PATCH lives in QuoteRequestControllerTest. This one keeps
		// the mechanism covered on its own, so a change here fails next to the code it broke.
		mvc.perform(patch("/api/quote-requests/{id}/probe", MINE))
				.andExpect(status().isUnauthorized());

		mvc.perform(patch("/api/quote-requests/{id}/probe", MINE).cookie(session(MINE)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("a tampered cookie is no cookie")
	void aTamperedCookieIsRefused() throws Exception {
		String forged = cookie.mint(MINE).replace(MINE.toString(), SOMEBODY_ELSES.toString());

		mvc.perform(get("/api/quote-requests/{id}/probe", SOMEBODY_ELSES)
						.cookie(new Cookie(AnonymousSessionCookie.NAME, forged)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("a cookie by any other name is not read")
	void onlyTheNamedCookieCounts() throws Exception {
		mvc.perform(get("/api/quote-requests/{id}/probe", MINE)
						.cookie(new Cookie("session", cookie.mint(MINE))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("an id that is not a UUID is refused like any other stranger's id")
	void aMalformedIdIsRefused() throws Exception {
		mvc.perform(get("/api/quote-requests/{id}/probe", "not-a-uuid").cookie(session(MINE)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("the issued cookie is the one §7 describes")
	void theIssuedCookieIsCorrect() {
		String header = cookie.asCookie(MINE).toString();

		assertThat(header).startsWith(AnonymousSessionCookie.NAME + "=");
		assertThat(header).contains("HttpOnly").contains("SameSite=Lax").contains("Secure")
				.contains("Path=/");
		assertThat(header)
				.as("a cookie is not scoped by port, so a host-only one still reaches the API in dev")
				.doesNotContain("Domain=");
	}

	private Cookie session(UUID id) {
		return new Cookie(AnonymousSessionCookie.NAME, cookie.mint(id));
	}
}
