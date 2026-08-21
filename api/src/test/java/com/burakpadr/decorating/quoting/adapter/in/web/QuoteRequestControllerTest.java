package com.burakpadr.decorating.quoting.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.config.session.AnonymousSessionCookie;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * §7's first two anonymous routes (BOYA-25).
 *
 * <p>The ticket's acceptance is that every step reaches the server, so the assertions follow one form
 * across two calls: what the second call did not mention is still there afterwards. The other half is
 * the session, which BOYA-24 built and this is the first real user of — the POST hands out the cookie
 * and the PATCH is useless without it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class QuoteRequestControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private AnonymousSessionCookie session;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	@Test
	@DisplayName("POST starts a draft and hands back the session that owns it")
	void createIssuesTheSession() throws Exception {
		String cookie = mvc.perform(post("/api/quote-requests"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.priceable")
						.value(false))
				.andExpect(header().exists(HttpHeaders.SET_COOKIE))
				.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		assertThat(cookie)
				.as("a caller that kept the id and dropped this header would hold an id it can never use")
				.startsWith(AnonymousSessionCookie.NAME + "=")
				.contains("HttpOnly").contains("SameSite=Lax");
	}

	@Test
	@DisplayName("POST needs no cookie — it is the one route that gives one out")
	void createNeedsNoSession() throws Exception {
		mvc.perform(post("/api/quote-requests")).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("acceptance: an answer given on one call is still there on the next")
	void answersAccumulateOnTheServer() throws Exception {
		UUID id = draft();

		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"districtCode\":\"KADIKOY\",\"area\":92,\"areaBasis\":\"NET\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.districtCode").value("KADIKOY"))
				.andExpect(jsonPath("$.priceable").value(false));

		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"layout\":\"THREE_PLUS_ONE\",\"scope\":\"WHOLE_HOME\","
								+ "\"furnishing\":\"FURNISHED\",\"wallCondition\":\"MINOR\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.layout").value("THREE_PLUS_ONE"))
				.andExpect(jsonPath("$.districtCode")
						.value("KADIKOY"))
				.andExpect(jsonPath("$.area").value(92))
				.andExpect(jsonPath("$.priceable")
						.value(true));

		// And it is on the server, not in a response the client could have assembled itself.
		assertThat(requests.findById(id).orElseThrow().answers().districtCode()).isEqualTo("KADIKOY");
	}

	@Test
	@DisplayName("zero doors is stored as an answer, not as silence")
	void zeroDoorsIsAnAnswer() throws Exception {
		UUID id = draft();

		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"doorCount\":0,\"doorColourChange\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.doorCount").value(0))
				.andExpect(jsonPath("$.doorColourChange").value(false));
	}

	@Test
	@DisplayName("acceptance: without the cookie, PATCH does not work")
	void patchNeedsTheSession() throws Exception {
		UUID id = draft();

		mvc.perform(patch("/api/quote-requests/{id}", id)
						.contentType(MediaType.APPLICATION_JSON).content("{\"area\":92}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("acceptance: a session cannot answer somebody else's draft")
	void patchRefusesAnotherDraft() throws Exception {
		UUID mine = draft();
		UUID theirs = draft();

		mvc.perform(patch("/api/quote-requests/{id}", theirs).cookie(owns(mine))
						.contentType(MediaType.APPLICATION_JSON).content("{\"area\":92}"))
				.andExpect(status().isForbidden());

		assertThat(requests.findById(theirs).orElseThrow().answers().areaInput())
				.as("and nothing of theirs was written on the way to being refused")
				.isNull();
	}

	@Test
	@DisplayName("answers are fixed once the room list is confirmed: 409, not a silent no-op")
	void patchRefusesAConfirmedRequest() throws Exception {
		UUID id = draft();
		requests.save(requests.findById(id).orElseThrow().confirmRoomList());

		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON).content("{\"area\":40}"))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("a valid session for a request that no longer exists is 404")
	void patchOfADeletedRequestIsNotFound() throws Exception {
		UUID gone = Uuid7.generate();

		mvc.perform(patch("/api/quote-requests/{id}", gone).cookie(owns(gone))
						.contentType(MediaType.APPLICATION_JSON).content("{\"area\":92}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("an answer that cannot be true is refused before it is stored")
	void refusesImpossibleAnswers() throws Exception {
		UUID id = draft();

		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON).content("{\"doorCount\":500}"))
				.andExpect(status().isBadRequest());
		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON).content("{\"area\":0}"))
				.andExpect(status().isBadRequest());
		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON).content("{\"layout\":\"BUNGALOW\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("an empty patch is allowed and changes nothing")
	void anEmptyPatchIsHarmless() throws Exception {
		UUID id = draft();
		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"districtCode\":\"KADIKOY\"}"))
				.andExpect(status().isOk());

		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.districtCode").value("KADIKOY"));
	}

	/** A stored draft, made directly rather than through the endpoint the test is not asserting about. */
	private UUID draft() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate());
		requests.save(draft);
		return draft.id();
	}

	private Cookie owns(UUID id) {
		return new Cookie(AnonymousSessionCookie.NAME, session.mint(id));
	}
}
