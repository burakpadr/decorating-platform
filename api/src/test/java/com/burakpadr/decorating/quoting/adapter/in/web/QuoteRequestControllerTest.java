package com.burakpadr.decorating.quoting.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.config.session.AnonymousSessionCookie;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
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

	@Test
	@DisplayName("GET answers the draft, so the result screen survives a reload")
	void showAnswersTheDraft() throws Exception {
		UUID id = answered();

		mvc.perform(get("/api/quote-requests/{id}", id).cookie(owns(id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.districtCode").value("KADIKOY"))
				.andExpect(jsonPath("$.wallCondition").value("MINOR"))
				.andExpect(jsonPath("$.priceable").value(true));
	}

	@Test
	@DisplayName("GET is scoped like every other route: the session, or nothing")
	void showNeedsTheSession() throws Exception {
		UUID id = answered();

		mvc.perform(get("/api/quote-requests/{id}", id))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/quote-requests/{id}", id).cookie(owns(draft())))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("POST /{id}/estimate answers a range, the areas, and how wide the band is")
	void estimateAnswersTheRange() throws Exception {
		UUID id = answered();

		mvc.perform(post("/api/quote-requests/{id}/estimate", id).cookie(owns(id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.low").isNumber())
				.andExpect(jsonPath("$.high").isNumber())
				.andExpect(jsonPath("$.bandRatio").value(0.12))
				.andExpect(jsonPath("$.netArea").value(92.00))
				.andExpect(jsonPath("$.areaWasGross").value(false))
				.andExpect(jsonPath("$.rooms.length()").value(7))
				.andExpect(jsonPath("$.rooms[0].label").value("Salon"))
				.andExpect(jsonPath("$.photoCount").value(28));
	}

	@Test
	@DisplayName("§1: the customer's answer carries no cost and no margin")
	void estimateLeaksNoCost() throws Exception {
		UUID id = answered();

		String body = mvc.perform(post("/api/quote-requests/{id}/estimate", id).cookie(owns(id)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		// Asserted rather than assumed. The field that leaks is the one somebody adds while debugging,
		// and this response is one screenshot away from a conversation about what the job costs us.
		assertThat(body).doesNotContain("totalCost").doesNotContain("margin")
				.doesNotContain("subtotal").doesNotContain("vat").doesNotContain("lines")
				.doesNotContain("labour").doesNotContain("material").doesNotContain("minimum")
				.doesNotContain("billableDays").doesNotContain("priceBookVersion");
	}

	@Test
	@DisplayName("an unfinished draft cannot be priced: 409, not a number built on a default")
	void estimateRefusesAnUnfinishedDraft() throws Exception {
		UUID id = draft();

		mvc.perform(post("/api/quote-requests/{id}/estimate", id).cookie(owns(id)))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("the estimate needs the session like every other scoped route")
	void estimateNeedsTheSession() throws Exception {
		UUID id = answered();

		mvc.perform(post("/api/quote-requests/{id}/estimate", id))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/quote-requests/{id}/estimate", id).cookie(owns(draft())))
				.andExpect(status().isForbidden());
	}

	// =============================================================================================
	// Service area (BOYA-27)
	// =============================================================================================

	@Test
	@DisplayName("acceptance: a district we do not serve cannot be answered at all")
	void refusesAnUnservedDistrict() throws Exception {
		UUID id = draft();

		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"districtCode\":\"MARS\",\"area\":92}"))
				.andExpect(status().isUnprocessableEntity())
				// The client has to recognise this without matching on a Turkish sentence: workflow §8
				// turns this screen into the waitlist offer.
				.andExpect(jsonPath("$.type").value("urn:decorating:district-not-served"))
				.andExpect(jsonPath("$.districtCode").value("MARS"));

		assertThat(requests.findById(id).orElseThrow().answers().areaInput())
				.as("and nothing else in the same patch was written on the way to being refused")
				.isNull();
	}

	@Test
	@DisplayName("a district that has been switched off is refused like one that never existed")
	void refusesAClosedDistrict() throws Exception {
		UUID id = draft();
		jdbc.update("""
				INSERT INTO service_district (id, price_book_id, district_code, display_name, active,
				  district_factor)
				SELECT ?, id, 'TEST_CLOSED', 'Kapalı İlçe', false, 1.0000
				FROM price_book WHERE active = true
				""", UUID.randomUUID());
		try {
			mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"districtCode\":\"TEST_CLOSED\"}"))
					.andExpect(status().isUnprocessableEntity());
		} finally {
			jdbc.update("DELETE FROM service_district WHERE district_code = 'TEST_CLOSED'");
		}
	}

	@Test
	@DisplayName("a patch about something else does not have to name a district")
	void aPatchWithoutADistrictIsFine() throws Exception {
		UUID id = draft();

		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON).content("{\"area\":92}"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("the estimate refuses a district that closed after it was answered")
	void estimateRefusesADistrictThatClosed() throws Exception {
		UUID id = answered();
		// The draft was answered while Kadıköy was open; the business closes it while the customer is
		// still on the form. PriceBook.districtFactor would price an unlisted district at 1.0000, so
		// without the second check the customer is quoted for an area nobody will drive to.
		jdbc.update("""
				UPDATE service_district SET active = false
				WHERE district_code = 'KADIKOY'
				  AND price_book_id = (SELECT id FROM price_book WHERE active = true)
				""");
		try {
			mvc.perform(post("/api/quote-requests/{id}/estimate", id).cookie(owns(id)))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.type").value("urn:decorating:district-not-served"));
		} finally {
			jdbc.update("""
					UPDATE service_district SET active = true
					WHERE district_code = 'KADIKOY'
					  AND price_book_id = (SELECT id FROM price_book WHERE active = true)
					""");
		}
	}

	// =============================================================================================
	// §1.5's third option, and the handoff it needs (BOYA-33)
	// =============================================================================================

	@Test
	@DisplayName("POST /{id}/estimate-sms keeps the number and queues the message")
	void sendsTheEstimateBySms() throws Exception {
		UUID id = answered();
		mvc.perform(post("/api/quote-requests/{id}/estimate", id).cookie(owns(id)));

		mvc.perform(post("/api/quote-requests/{id}/estimate-sms", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"0555 123 45 67\"}"))
				// 202, not 200: nothing has been delivered and nothing should claim it was.
				.andExpect(status().isAccepted());

		assertThat(jdbc.queryForObject(
						"SELECT status FROM notification WHERE quote_request_id = ?", String.class, id))
				.isEqualTo("QUEUED");
	}

	@Test
	@DisplayName("a landline is refused before anything is stored")
	void refusesANumberNoSmsReaches() throws Exception {
		UUID id = answered();

		mvc.perform(post("/api/quote-requests/{id}/estimate-sms", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"02121234567\"}"))
				.andExpect(status().isBadRequest());

		assertThat(jdbc.queryForObject(
						"SELECT pending_phone FROM quote_request WHERE id = ?", String.class, id))
				.isNull();
	}

	@Test
	@DisplayName("somebody else's draft cannot be sent to my phone")
	void refusesSendingSomebodyElsesEstimate() throws Exception {
		UUID mine = answered();
		UUID theirs = answered();

		mvc.perform(post("/api/quote-requests/{id}/estimate-sms", theirs).cookie(owns(mine))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"phone\":\"05551234567\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("GET /resume/{token} grants a session on the device holding the link")
	void resumeGrantsASession() throws Exception {
		UUID id = answered();
		mvc.perform(post("/api/quote-requests/{id}/estimate", id).cookie(owns(id)));
		mvc.perform(post("/api/quote-requests/{id}/estimate-sms", id).cookie(owns(id))
				.contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"05551234567\"}"));
		String token = jdbc.queryForObject(
				"SELECT resume_token FROM quote_request WHERE id = ?", String.class, id);

		// No cookie on this request at all: that is the point — the phone has never seen one.
		mvc.perform(get("/api/quote-requests/resume/{token}", token))
				.andExpect(status().isOk())
				.andExpect(header().exists(HttpHeaders.SET_COOKIE))
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.districtCode").value("KADIKOY"));
	}

	@Test
	@DisplayName("an invented token is 404, not somebody's draft")
	void resumeRefusesAnUnknownToken() throws Exception {
		mvc.perform(get("/api/quote-requests/resume/{token}", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
				.andExpect(status().isNotFound());
	}

	// =============================================================================================
	// The areas to photograph (BOYA-37)
	// =============================================================================================

	@Test
	@DisplayName("POST /{id}/rooms/confirm answers the labelled list and what it will cost in frames")
	void confirmsTheRoomList() throws Exception {
		UUID id = answered();
		mvc.perform(post("/api/quote-requests/{id}/estimate", id).cookie(owns(id)));

		mvc.perform(post("/api/quote-requests/{id}/rooms/confirm", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"areas\":[\"LIVING_ROOM\",\"BEDROOM\",\"BEDROOM\",\"BATHROOM\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rooms.length()").value(4))
				.andExpect(jsonPath("$.rooms[0].label").value("Salon"))
				.andExpect(jsonPath("$.rooms[1].label").value("Yatak odası 1"))
				.andExpect(jsonPath("$.rooms[3].requiredPhotos.length()").value(2))
				// §2.2 wants this on the screen with the list: 5 + 5 + 5 + 2.
				.andExpect(jsonPath("$.photoCount").value(17));
	}

	@Test
	@DisplayName("the client cannot name the rooms: labels are the server's copy")
	void ignoresAnyLabelTheClientSends() throws Exception {
		UUID id = answered();
		mvc.perform(post("/api/quote-requests/{id}/estimate", id).cookie(owns(id)));

		mvc.perform(post("/api/quote-requests/{id}/rooms/confirm", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"areas\":[\"KITCHEN\"],\"label\":\"Şahane mutfağım\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rooms[0].label").value("Mutfak"));
	}

	@Test
	@DisplayName("an empty list is refused before anything is written")
	void refusesAnEmptyRoomList() throws Exception {
		UUID id = answered();

		mvc.perform(post("/api/quote-requests/{id}/rooms/confirm", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON).content("{\"areas\":[]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("confirming somebody else's list is refused like every other scoped route")
	void confirmNeedsTheSession() throws Exception {
		UUID id = answered();

		mvc.perform(post("/api/quote-requests/{id}/rooms/confirm", id)
						.contentType(MediaType.APPLICATION_JSON).content("{\"areas\":[\"KITCHEN\"]}"))
				.andExpect(status().isUnauthorized());
		mvc.perform(post("/api/quote-requests/{id}/rooms/confirm", id).cookie(owns(draft()))
						.contentType(MediaType.APPLICATION_JSON).content("{\"areas\":[\"KITCHEN\"]}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("once confirmed the answers are fixed: PATCH is refused")
	void answersAreFixedAfterConfirmation() throws Exception {
		UUID id = answered();
		mvc.perform(post("/api/quote-requests/{id}/estimate", id).cookie(owns(id)));
		mvc.perform(post("/api/quote-requests/{id}/rooms/confirm", id).cookie(owns(id))
				.contentType(MediaType.APPLICATION_JSON).content("{\"areas\":[\"KITCHEN\"]}"));

		// The list was derived from the answers and the photographs will be taken against it, so an
		// answer changed now invalidates everything downstream with nothing to show it happened.
		mvc.perform(patch("/api/quote-requests/{id}", id).cookie(owns(id))
						.contentType(MediaType.APPLICATION_JSON).content("{\"area\":40}"))
				.andExpect(status().isConflict());
	}

	/** A draft with all of §2.1 answered, which is what the estimate needs. */
	private UUID answered() {
		UUID id = draft();
		requests.save(requests.findById(id).orElseThrow().answer(new StageOneAnswers(
				"KADIKOY", new java.math.BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, WallCondition.MINOR, null)));
		return id;
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
