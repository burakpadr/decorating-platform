package com.burakpadr.decorating.quoting.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.config.session.AnonymousSessionCookie;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.CapturedFrame;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.PhotoUploadIntent;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.CapturePhotos;
import com.burakpadr.decorating.quoting.domain.port.in.ConfirmRoomList;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.in.ReadConsentNotice;
import com.burakpadr.decorating.quoting.domain.port.in.RecordConsent;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/quote-requests/{id}/rooms} — what the capture screen opens on (§2.4, BOYA-42).
 *
 * <p>The 401 and 403 are not asserted here: {@code QuoteRequestEndpointsAreOwnedTest} enumerates every
 * route under this controller and fails on one written without an {@code OwnedQuoteRequest}, so
 * repeating it per endpoint would be a second, weaker copy of a rule that is already enforced.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CaptureStateEndpointTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private CapturePhotos photos;

	@Autowired
	private ConfirmRoomList rooms;

	@Autowired
	private EstimateStageOne estimates;

	@Autowired
	private RecordConsent consents;

	@Autowired
	private ReadConsentNotice notices;

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

	private record Capturing(UUID id, ConfirmedRooms areas) {}

	private Capturing capturing() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 3, false, WallCondition.MINOR, null));
		requests.save(draft);
		estimates.estimate(draft.id());
		ConfirmedRooms agreed = rooms.confirm(draft.id(),
				List.of(RoomType.LIVING_ROOM, RoomType.BATHROOM));
		consents.record(draft.id(), ConsentType.PROCESSING, true,
				notices.current(ConsentType.PROCESSING).version());
		return new Capturing(draft.id(), agreed);
	}

	@Test
	@DisplayName("the screen is told the areas, their frames and the totals it has to show")
	void answersTheCaptureState() throws Exception {
		Capturing session = capturing();

		mvc.perform(get("/api/quote-requests/{id}/rooms", session.id()).cookie(owns(session.id())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.required").value(7))
				.andExpect(jsonPath("$.taken").value(0))
				.andExpect(jsonPath("$.complete").value(false))
				.andExpect(jsonPath("$.areas[0].label").value("Salon"))
				.andExpect(jsonPath("$.areas[0].frames.length()").value(5))
				.andExpect(jsonPath("$.areas[0].frames[0].role").value("WALL_1"))
				.andExpect(jsonPath("$.areas[0].frames[0].taken").value(false))
				.andExpect(jsonPath("$.areas[1].label").value("Banyo"))
				.andExpect(jsonPath("$.areas[1].frames.length()").value(2));
	}

	@Test
	@DisplayName("a frame that arrived names its photograph, so the screen can offer a retake")
	void namesThePhotographOfATakenFrame() throws Exception {
		Capturing capture = capturing();
		UUID roomId = capture.areas().rooms().getFirst().id();
		PhotoUploadIntent intent = photos.intend(capture.id(), roomId, PhotoRole.WALL_1);
		photos.complete(capture.id(), intent.photo().id(),
				new CapturedFrame(null, 2048, 1536, 240_000, new BigDecimal("96.10"), false));

		mvc.perform(get("/api/quote-requests/{id}/rooms", capture.id()).cookie(owns(capture.id())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.taken").value(1))
				.andExpect(jsonPath("$.areas[0].frames[0].taken").value(true))
				.andExpect(jsonPath("$.areas[0].frames[0].photoId").value(intent.photo().id().toString()));
	}
}
