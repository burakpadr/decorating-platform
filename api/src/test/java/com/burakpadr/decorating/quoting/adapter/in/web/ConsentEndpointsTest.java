package com.burakpadr.decorating.quoting.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.config.session.AnonymousSessionCookie;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.ConfirmRoomList;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.in.ReadConsentNotice;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The two routes §2.3 needs, neither of which §7 lists (decision 0015 is the precedent, BOYA-39).
 *
 * <p>They are split across two realms on purpose. The notice is a public text with no session — asking
 * for a cookie to read what the business does with photographs would be a strange thing to require, and
 * the screen shows it before the customer has decided anything. The grant is the opposite: it is a fact
 * about one request, and only the session that owns that request may write it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsentEndpointsTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ReadConsentNotice notices;

	@Autowired
	private ConfirmRoomList rooms;

	@Autowired
	private EstimateStageOne estimates;

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

	private Cookie owns(UUID id) {
		return new Cookie(AnonymousSessionCookie.NAME, session.asCookie(id).getValue());
	}

	/** A request with its room list agreed, which is where §2.3 picks up. */
	private UUID awaitingPhotographs() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 3, false, WallCondition.MINOR, null));
		requests.save(draft);
		estimates.estimate(draft.id());
		rooms.confirm(draft.id(), List.of(RoomType.LIVING_ROOM, RoomType.BATHROOM));
		return draft.id();
	}

	private String currentVersion() {
		return notices.current(ConsentType.PROCESSING).version();
	}

	@Test
	@DisplayName("the notice is readable without a session: it is what the screen shows before deciding")
	void servesTheNoticeAnonymously() throws Exception {
		mvc.perform(get("/api/consent-notices/PROCESSING"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("PROCESSING"))
				.andExpect(jsonPath("$.textVersion").value(currentVersion()))
				.andExpect(jsonPath("$.body").value(org.hamcrest.Matchers.containsString("30 gün")));
	}

	@Test
	@DisplayName("§2.3: the session that owns the request records its decision")
	void recordsAGrant() throws Exception {
		UUID id = awaitingPhotographs();

		mvc.perform(post("/api/quote-requests/{id}/consents", id)
				.cookie(owns(id))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"PROCESSING","granted":true,"textVersion":"%s"}
						""".formatted(currentVersion())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.granted").value(true))
				.andExpect(jsonPath("$.textVersion").value(currentVersion()));
	}

	@Test
	@DisplayName("no session, no grant — a consent is a fact about somebody's request")
	void refusesAGrantWithoutASession() throws Exception {
		UUID id = awaitingPhotographs();

		mvc.perform(post("/api/quote-requests/{id}/consents", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"PROCESSING","granted":true,"textVersion":"%s"}
						""".formatted(currentVersion())))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("a notice that moved on is answered with the version to show instead, not a 409 about answers")
	void answersAStaleNoticeWithTheCurrentVersion() throws Exception {
		UUID id = awaitingPhotographs();

		mvc.perform(post("/api/quote-requests/{id}/consents", id)
				.cookie(owns(id))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"PROCESSING","granted":true,"textVersion":"v0-never-published"}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("urn:decorating:consent-notice-changed"))
				.andExpect(jsonPath("$.currentVersion").value(currentVersion()));
	}

	@Test
	@DisplayName("acceptance: photographs cannot be reserved until the notice has been agreed to")
	void refusesAnUploadIntentBeforeConsent() throws Exception {
		UUID id = awaitingPhotographs();
		UUID roomId = jdbc.queryForObject(
				"SELECT id FROM room WHERE quote_request_id = ? ORDER BY sort_order LIMIT 1",
				UUID.class, id);

		mvc.perform(post("/api/photos/upload-intent")
				.cookie(owns(id))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"roomId":"%s","role":"WALL_1"}
						""".formatted(roomId)))
				.andExpect(status().isForbidden());

		mvc.perform(post("/api/quote-requests/{id}/consents", id)
				.cookie(owns(id))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"PROCESSING","granted":true,"textVersion":"%s"}
						""".formatted(currentVersion())))
				.andExpect(status().isCreated());

		mvc.perform(post("/api/photos/upload-intent")
				.cookie(owns(id))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"roomId":"%s","role":"WALL_1"}
						""".formatted(roomId)))
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("a refusal also closes the door: withdrawing consent stops the next frame")
	void refusesAnUploadIntentAfterARefusal() throws Exception {
		UUID id = awaitingPhotographs();
		UUID roomId = jdbc.queryForObject(
				"SELECT id FROM room WHERE quote_request_id = ? ORDER BY sort_order LIMIT 1",
				UUID.class, id);

		mvc.perform(post("/api/quote-requests/{id}/consents", id)
				.cookie(owns(id))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"type":"PROCESSING","granted":false,"textVersion":"%s"}
						""".formatted(currentVersion())))
				.andExpect(status().isCreated());

		mvc.perform(post("/api/photos/upload-intent")
				.cookie(owns(id))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"roomId":"%s","role":"WALL_1"}
						""".formatted(roomId)))
				.andExpect(status().isForbidden());
	}
}
