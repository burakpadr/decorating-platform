package com.burakpadr.decorating.quoting.adapter.in.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.config.session.AnonymousSessionCookie;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.ConfirmRoomList;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoStorage;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * §7's three photo routes (BOYA-40).
 *
 * <p>These are the only anonymous routes that do not name a quote request in the path — §7 puts them
 * under {@code /api/photos}, so the id in the URL is a photograph and the session is what says whose
 * it is. That is the whole risk of this controller and most of what is asserted here: a photo id is a
 * row, not a credential.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PhotoControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ConfirmRoomList roomList;

	@Autowired
	private EstimateStageOne estimates;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private AnonymousSessionCookie session;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private PhotoStorage storage;

	@BeforeEach
	void storageSigns() {
		given(storage.presignPut(anyString())).willReturn(
				new PresignedUrl(URI.create("http://storage.test/put"), Duration.ofMinutes(15)));
		given(storage.presignGet(anyString())).willReturn(
				new PresignedUrl(URI.create("http://storage.test/get"), Duration.ofMinutes(5)));
	}

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	private Cookie owns(UUID id) {
		return new Cookie(AnonymousSessionCookie.NAME, session.asCookie(id).getValue());
	}

	/** A request with its room list agreed, which is the only state a photograph belongs in. */
	private ConfirmedRooms capturing() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 6, false, WallCondition.GOOD, null));
		requests.save(draft);
		estimates.estimate(draft.id());
		return roomList.confirm(draft.id(), List.of(
				RoomType.LIVING_ROOM, RoomType.KITCHEN, RoomType.BATHROOM, RoomType.HALLWAY));
	}

	private UUID requestOf(ConfirmedRooms rooms) {
		return jdbc.queryForObject("SELECT quote_request_id FROM room WHERE id = ?", UUID.class,
				rooms.rooms().getFirst().id());
	}

	private String intendBody(UUID roomId, String role) {
		return "{\"roomId\":\"" + roomId + "\",\"role\":\"" + role + "\"}";
	}

	@Test
	@DisplayName("POST /upload-intent answers a photo id and the URL the browser PUTs to")
	void intentAnswersAPresignedUrl() throws Exception {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);

		mvc.perform(post("/api/photos/upload-intent").cookie(owns(request))
						.contentType(MediaType.APPLICATION_JSON)
						.content(intendBody(rooms.rooms().getFirst().id(), "WALL_1")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.photoId").isNotEmpty())
				.andExpect(jsonPath("$.uploadUrl").value("http://storage.test/put"))
				// The client needs the window, not the clock: an expired URL fails in the browser with a
				// signature error nobody can act on, and knowing it is expired is what makes a retry sane.
				.andExpect(jsonPath("$.expiresInSeconds").value(900));
	}

	@Test
	@DisplayName("the photo routes need the session like every other scoped route")
	void intentNeedsTheSession() throws Exception {
		ConfirmedRooms rooms = capturing();

		mvc.perform(post("/api/photos/upload-intent")
						.contentType(MediaType.APPLICATION_JSON)
						.content(intendBody(rooms.rooms().getFirst().id(), "WALL_1")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("a room from another request is refused, and told nothing about it")
	void refusesSomebodyElsesRoom() throws Exception {
		ConfirmedRooms mine = capturing();
		ConfirmedRooms theirs = capturing();

		mvc.perform(post("/api/photos/upload-intent").cookie(owns(requestOf(mine)))
						.contentType(MediaType.APPLICATION_JSON)
						.content(intendBody(theirs.rooms().getFirst().id(), "WALL_1")))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("POST /{id}/complete records the upload and what the client measured")
	void completeMarksItUploaded() throws Exception {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		String photoId = intend(request, rooms.rooms().getFirst().id(), "CEILING");

		mvc.perform(post("/api/photos/{id}/complete", photoId).cookie(owns(request))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"capturedAt":"2026-08-24T07:30:00Z","width":2048,"height":1152,
								 "byteSize":388120,"qualityScore":41.55,"lowQualityFlag":true}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uploaded").value(true))
				.andExpect(jsonPath("$.role").value("CEILING"))
				.andExpect(jsonPath("$.lowQualityFlag").value(true))
				// The key is the server's business. A client that knew it could sign nothing with it, but
				// it is also the one field that says where somebody's home is stored.
				.andExpect(jsonPath("$.storageKey").doesNotExist());
	}

	@Test
	@DisplayName("completing a photograph belonging to another session is refused")
	void refusesToCompleteSomebodyElsesPhoto() throws Exception {
		ConfirmedRooms mine = capturing();
		ConfirmedRooms theirs = capturing();
		String theirPhoto = intend(requestOf(theirs), theirs.rooms().getFirst().id(), "WALL_1");

		mvc.perform(post("/api/photos/{id}/complete", theirPhoto).cookie(owns(requestOf(mine)))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"width\":2048,\"height\":1536,\"byteSize\":300000}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("DELETE is the retake, and answers nothing")
	void deleteIsTheRetake() throws Exception {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		String photoId = intend(request, rooms.rooms().getFirst().id(), "WALL_2");

		mvc.perform(delete("/api/photos/{id}", photoId).cookie(owns(request)))
				.andExpect(status().isNoContent());

		mvc.perform(delete("/api/photos/{id}", photoId).cookie(owns(request)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("a frame already uploaded is 409, not a silent overwrite")
	void refusesToReplaceAnUploadedFrame() throws Exception {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		UUID room = rooms.rooms().getFirst().id();
		String photoId = intend(request, room, "WALL_3");
		mvc.perform(post("/api/photos/{id}/complete", photoId).cookie(owns(request))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"width\":2048,\"height\":1536,\"byteSize\":300000}"));

		mvc.perform(post("/api/photos/upload-intent").cookie(owns(request))
						.contentType(MediaType.APPLICATION_JSON)
						.content(intendBody(room, "WALL_3")))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("a request that has not agreed a room list cannot photograph anything")
	void refusesADraft() throws Exception {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate());
		requests.save(draft);

		mvc.perform(post("/api/photos/upload-intent").cookie(owns(draft.id()))
						.contentType(MediaType.APPLICATION_JSON)
						.content(intendBody(Uuid7.generate(), "WALL_1")))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("§9: the customer's session does not open the operator's read")
	void theCustomerSessionDoesNotOpenTheRead() throws Exception {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		String photoId = intend(request, rooms.rooms().getFirst().id(), "WALL_4");

		// Nothing in the customer flow reads a photograph back — the phone still has the original — and
		// a cookie that opened the operator realm would be a way into everybody else's photographs too.
		mvc.perform(get("/api/op/photos/{id}", photoId).cookie(owns(request)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	@DisplayName("§9: the operator's read is presigned and short-lived")
	void theOperatorReadIsPresigned() throws Exception {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		String photoId = intend(request, rooms.rooms().getFirst().id(), "WALL_4");

		mvc.perform(get("/api/op/photos/{id}", photoId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("http://storage.test/get"))
				// Five minutes: long enough for the screen it was made for, short enough that a link
				// pasted into a message is a link to nothing by the time anybody opens it.
				.andExpect(jsonPath("$.expiresInSeconds").value(300));
	}

	private String intend(UUID request, UUID roomId, String role) throws Exception {
		String body = mvc.perform(post("/api/photos/upload-intent").cookie(owns(request))
						.contentType(MediaType.APPLICATION_JSON)
						.content(intendBody(roomId, role)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return body.replaceAll(".*\"photoId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
	}
}
