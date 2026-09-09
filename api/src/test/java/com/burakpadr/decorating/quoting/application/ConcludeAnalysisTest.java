package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.CapturedFrame;
import com.burakpadr.decorating.quoting.domain.model.CeilingFinding;
import com.burakpadr.decorating.quoting.domain.model.Coating;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms.ConfirmedRoom;
import com.burakpadr.decorating.quoting.domain.model.CrackLevel;
import com.burakpadr.decorating.quoting.domain.model.FillerBand;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.SurfaceFinding;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.ConcludeAnalysis;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoRepository;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomAnalysisRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The end of stage 4, wired (workflow §4.2–4.3, BOYA-51).
 *
 * <p>§6's arithmetic is {@code ConfidenceEvaluatorTest}'s, pure and with a case per branch. What is
 * asserted here is the part that needs rows: which analysis triggers the conclusion, what gets written
 * when it does, and where the request ends up.
 *
 * <p>The last assertion of most of these is the status, and one of them is the point of the whole
 * card: <b>a survey is a mark on the queue and not a status</b>. §3 draws SURVEY_REQUIRED from
 * PENDING_REVIEW, and workflow §4.3 says the survey case lands in the queue with a survey mark — so
 * AUTO and SURVEY differ in a column, and the operator is the one who converts (BOYA-54).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcludeAnalysisTest {

	@Autowired
	private ConcludeAnalysis conclusion;

	@Autowired
	private RoomAnalysisRepository analyses;

	@Autowired
	private RoomRepository rooms;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private EstimateStageOne estimateStageOne;

	@Autowired
	private PhotoRepository photos;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID quoteRequestId;
	private final List<ConfirmedRoom> confirmed = new ArrayList<>();

	@BeforeEach
	void aRequestBeingAnalysed() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, WallCondition.MINOR, null));
		requests.save(draft);
		quoteRequestId = draft.id();
		estimateStageOne.estimate(quoteRequestId);
		// Through the transitions, so the fixture cannot describe a state §3 would refuse.
		requests.save(draft.confirmRoomList().submit());
	}

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	@Test
	@DisplayName("the last analysis prices the home, records the decision and hands it to the queue")
	void concludesWhenEveryRoomIsIn() {
		List<UUID> home = areas(RoomType.LIVING_ROOM, RoomType.BEDROOM);
		analyse(home.get(0), sound());
		analyse(home.get(1), sound());

		conclusion.concludeIfComplete(quoteRequestId);

		assertThat(status()).isEqualTo("PENDING_REVIEW");
		assertThat(decision()).isEqualTo("AUTO");
		assertThat(reasons()).isEmpty();
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM quote WHERE quote_request_id = ?
				""", Integer.class, quoteRequestId)).isOne();
	}

	@Test
	@DisplayName("a room still being analysed leaves the request where it was, with no quote")
	void waitsForTheRestOfTheHome() {
		// The reason this is one use case and not three calls from the poller: priced now, the home
		// would be quoted for the rooms that happened to finish first.
		List<UUID> home = areas(RoomType.LIVING_ROOM, RoomType.BEDROOM);
		analyse(home.get(0), sound());

		conclusion.concludeIfComplete(quoteRequestId);

		assertThat(status()).isEqualTo("ANALYZING");
		assertThat(decision()).isNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM quote", Integer.class)).isZero();
	}

	@Test
	@DisplayName("an unusable frame goes back to the customer, once, and the count says so")
	void asksForTheFrameAgain() {
		UUID bedroom = areas(RoomType.BEDROOM).getFirst();
		analyses.save(analysis(bedroom, List.of(sound()), List.of("WALL_2")));

		conclusion.concludeIfComplete(quoteRequestId);

		assertThat(status()).isEqualTo("RECAPTURE_REQUIRED");
		assertThat(decision()).isEqualTo("RECAPTURE");
		assertThat(reasons()).anyMatch(reason -> reason.contains("WALL_2"));
		assertThat(jdbc.queryForObject("""
				SELECT recapture_count FROM quote_request WHERE id = ?
				""", Integer.class, quoteRequestId)).isOne();
	}

	@Test
	@DisplayName("a risk finding is a mark on the queue, not a status of its own")
	void marksASurveyWithoutMovingTheRequest() {
		// The card's point. §6 reads as though SURVEY were a state; §3 draws SURVEY_REQUIRED from
		// PENDING_REVIEW and workflow §4.3 says it "kuyruğa keşif işaretiyle düşer". So the request goes
		// to the queue and the mark is what the operator sorts on and converts (BOYA-54).
		UUID bathroom = areas(RoomType.BATHROOM).getFirst();
		analyse(bathroom, new SurfaceFinding("ROOM_GENERAL", Coating.PAINTED, Tone.LIGHT,
				FillerBand.NONE, false, CrackLevel.NONE, Moisture.ACTIVE, false,
				new BigDecimal("0.950")));

		conclusion.concludeIfComplete(quoteRequestId);

		assertThat(status()).isEqualTo("PENDING_REVIEW");
		assertThat(decision()).isEqualTo("SURVEY");
		assertThat(reasons()).anyMatch(reason -> reason.contains("nem"));
		// Priced anyway: the operator needs a figure to look at, and §6 decides which range the
		// customer is shown rather than whether one exists.
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM quote WHERE quote_request_id = ?
				""", Integer.class, quoteRequestId)).isOne();
	}

	@Test
	@DisplayName("a request that is not being analysed is left alone")
	void ignoresARequestThatIsNotAnalysing() {
		// Called once per analysed room, so it will be called again after a recapture puts the request
		// back through PHOTOS_PENDING. Answering "too early" with an exception from the state machine
		// would be the wrong shape for it.
		analyse(areas(RoomType.BEDROOM).getFirst(), sound());
		conclusion.concludeIfComplete(quoteRequestId);
		assertThat(status()).isEqualTo("PENDING_REVIEW");

		conclusion.concludeIfComplete(quoteRequestId);

		assertThat(status()).isEqualTo("PENDING_REVIEW");
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM quote WHERE quote_request_id = ?
				""", Integer.class, quoteRequestId)).isOne();
	}

	// -----------------------------------------------------------------------------------------------

	private String status() {
		return jdbc.queryForObject(
				"SELECT status FROM quote_request WHERE id = ?", String.class, quoteRequestId);
	}

	private String decision() {
		return jdbc.queryForObject(
				"SELECT review_decision FROM quote_request WHERE id = ?", String.class, quoteRequestId);
	}

	private List<String> reasons() {
		return jdbc.queryForObject("SELECT review_reasons FROM quote_request WHERE id = ?",
				(row, index) -> List.of((String[]) row.getArray("review_reasons").getArray()),
				quoteRequestId);
	}

	private static SurfaceFinding sound() {
		return new SurfaceFinding("WALL_1", Coating.PAINTED, Tone.LIGHT, FillerBand.NONE, false,
				CrackLevel.NONE, Moisture.NONE, false, new BigDecimal("0.900"));
	}

	/**
	 * The home's areas, confirmed once, with every frame they were asked for uploaded.
	 *
	 * <p>Once, because {@code replaceAll} clears the request's rooms before writing them and the
	 * cascade takes their photographs with it — confirming areas one at a time leaves only the last
	 * one's frames behind. And with photographs at all, because §6's last risk finding is a room
	 * missing a frame it was asked for: a fixture that skips them describes a request that could never
	 * have reached ANALYZING, since {@code submit()} refuses an incomplete capture. The first draft of
	 * this file did both, and every case came back SURVEY — which is the check working.
	 *
	 * <p>Which frames those are comes from the price book's {@code room_type_config} and not from the
	 * list passed below: BOYA-37 deliberately keeps no second copy on the row.
	 */
	private List<UUID> areas(RoomType... types) {
		for (RoomType type : types) {
			confirmed.add(new ConfirmedRoom(Uuid7.generate(), type, type.name(), confirmed.size(),
					List.of(PhotoRole.WALL_1, PhotoRole.CEILING), true));
		}
		rooms.replaceAll(quoteRequestId, new ConfirmedRooms(confirmed));

		List<UUID> ids = new ArrayList<>();
		for (ConfirmedRoom room : rooms.findByQuoteRequest(quoteRequestId).rooms()) {
			ids.add(room.id());
			room.requiredPhotos().forEach(role -> upload(room.id(), role));
		}
		return ids;
	}

	private void upload(UUID roomId, PhotoRole role) {
		photos.save(Photo.intended(Uuid7.generate(), quoteRequestId, roomId, role)
				.uploaded(Instant.now(), new CapturedFrame(null, null, null, null, null, false)));
	}

	private void analyse(UUID roomId, SurfaceFinding... surfaces) {
		analyses.save(analysis(roomId, List.of(surfaces), List.of()));
	}

	private static RoomAnalysis analysis(UUID roomId, List<SurfaceFinding> surfaces,
			List<String> unusable) {
		return new RoomAnalysis(roomId, "v1", "test-model", "{}", surfaces, CeilingFinding.none(),
				new BigDecimal("0.900"), false, 0, Furnishing.FURNISHED, 1, 1, 1,
				new BigDecimal("0.900"), unusable, List.of());
	}
}
