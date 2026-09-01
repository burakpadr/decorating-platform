package com.burakpadr.decorating.quoting.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.CeilingFinding;
import com.burakpadr.decorating.quoting.domain.model.Coating;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms.ConfirmedRoom;
import com.burakpadr.decorating.quoting.domain.model.CrackLevel;
import com.burakpadr.decorating.quoting.domain.model.FillerBand;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.SurfaceFinding;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomAnalysisRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code room_analysis} and {@code surface_finding}, against a real Postgres (§4.4, BOYA-49).
 *
 * <p>Against the database rather than a mock because the acceptance criterion is a claim about rows:
 * "the engine does not parse JSON, it reads {@code surface_finding}". A mock would agree with whatever
 * the adapter happened to do. The test that settles it wipes {@code raw_response} after the write and
 * reads the findings back anyway — if any of them came from the JSON, that read returns a room with no
 * walls.
 *
 * <p>The CHECK constraints are exercised here too. V9 wrote the ceiling vocabulary into the schema
 * after {@code schema.json} and the domain had disagreed about it for the length of the project
 * (decision 0020); a constraint nobody has watched refuse anything is a constraint nobody knows is on.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RoomAnalysisPersistenceAdapterTest {

	@Autowired
	private RoomAnalysisRepository analyses;

	@Autowired
	private RoomRepository rooms;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID quoteRequestId;

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	@Test
	@DisplayName("every finding survives the round trip")
	void storesAndReadsBackAnAnalysis() {
		UUID room = aRoomOf(RoomType.LIVING_ROOM, 0);
		RoomAnalysis written = analysis(room, List.of(
				new SurfaceFinding("WALL_1", Coating.PAINTED, Tone.DARK, FillerBand.MEDIUM, false,
						CrackLevel.HAIRLINE, Moisture.NONE, false, new BigDecimal("0.880")),
				new SurfaceFinding("WALL_2", Coating.TILE, Tone.LIGHT, FillerBand.NONE, true,
						CrackLevel.STRUCTURAL, Moisture.ACTIVE, true, new BigDecimal("0.620"))));

		analyses.save(written);

		assertThat(analyses.findByRoom(room)).hasValueSatisfying(read -> {
			// Everything but the raw response, which comes back re-spaced: raw_response is jsonb, so
			// Postgres stores a parsed document and not the provider's bytes. Worth knowing before
			// anybody diffs two prompt versions' responses and finds differences nobody's model made.
			assertThat(read).usingRecursiveComparison().ignoringFields("rawResponse").isEqualTo(written);
			assertThat(read.rawResponse()).isEqualTo("{\"roomType\": \"BEDROOM\"}");
			// 0.880 and 0.620 at eye level, 0.700 overhead.
			assertThat(read.roomConfidence()).isEqualByComparingTo("0.733");
		});
	}

	@Test
	@DisplayName("the findings come from the columns, not from the JSON beside them")
	void readsRowsRatherThanRawResponse() {
		// §4.4's rule and this ticket's acceptance. raw_response is the audit trail; surface_finding is
		// what the engine and the calibration joins read. Emptying the JSON after the write is the only
		// way to prove which one the read path used.
		UUID room = aRoomOf(RoomType.BEDROOM, 0);
		analyses.save(analysis(room, List.of(surface("WALL_1", "0.900"))));

		jdbc.update("UPDATE room_analysis SET raw_response = '{}'::jsonb WHERE room_id = ?", room);

		assertThat(analyses.findByRoom(room)).hasValueSatisfying(read -> {
			assertThat(read.surfaces()).singleElement()
					.returns("WALL_1", SurfaceFinding::surfaceId)
					.returns(Coating.PAINTED, SurfaceFinding::coating);
			assertThat(read.ceiling().staining()).isEqualTo(Moisture.STAIN);
			assertThat(read.doorCount()).isEqualTo(2);
			assertThat(read.rawResponse()).isEqualTo("{}");
		});
	}

	@Test
	@DisplayName("§6's room confidence is on the row, so calibration can join rather than recompute")
	void storesTheAggregateConfidence() {
		UUID room = aRoomOf(RoomType.BEDROOM, 0);
		RoomAnalysis written = analysis(room,
				List.of(surface("WALL_1", "0.900"), surface("WALL_2", "0.600")));

		analyses.save(written);

		// 0.900, 0.600 and the ceiling's 0.700 — the average, not the worst (which would be 0.600).
		assertThat(jdbc.queryForObject(
				"SELECT confidence FROM room_analysis WHERE room_id = ?", BigDecimal.class, room))
				.isEqualByComparingTo("0.733");
		// And the model's own figure is kept apart from it, unrounded into the same meaning.
		assertThat(jdbc.queryForObject(
				"SELECT reported_confidence FROM room_analysis WHERE room_id = ?", BigDecimal.class, room))
				.isEqualByComparingTo("0.830");
	}

	@Test
	@DisplayName("re-analysing a room replaces it, leaving one analysis and its own surfaces")
	void replacesAPreviousAnalysis() {
		// What a recapture produces. room_id is UNIQUE, so the second reading is the reading — and the
		// first one's surface rows have to go with it, or the engine prices four walls in a two-wall room.
		UUID room = aRoomOf(RoomType.BEDROOM, 0);
		analyses.save(analysis(room, List.of(
				surface("WALL_1", "0.900"), surface("WALL_2", "0.900"), surface("WALL_3", "0.900"))));

		analyses.save(analysis(room, List.of(surface("WALL_1", "0.500"))));

		assertThat(analyses.findByRoom(room)).hasValueSatisfying(read ->
				assertThat(read.surfaces()).singleElement()
						.returns(new BigDecimal("0.500"), SurfaceFinding::confidence));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM surface_finding sf "
				+ "JOIN room_analysis ra ON ra.id = sf.room_analysis_id WHERE ra.room_id = ?",
				Integer.class, room)).isOne();
	}

	@Test
	@DisplayName("a request reads back its analysed rooms in capture order, and only those")
	void readsEveryAnalysedRoomOfARequest() {
		// What BOYA-50 asks for. A room still waiting for its analysis is simply absent — not a null in
		// the middle of the list, which is how half a home gets priced as a whole one.
		UUID hallway = aRoomOf(RoomType.HALLWAY, 0);
		UUID bedroom = aRoomOf(RoomType.BEDROOM, 1);
		aRoomOf(RoomType.KITCHEN, 2);

		analyses.save(analysis(bedroom, List.of(surface("WALL_1", "0.900"))));
		analyses.save(analysis(hallway, List.of(surface("ROOM_GENERAL", "0.700"))));

		assertThat(analyses.findByQuoteRequest(quoteRequestId))
				.extracting(RoomAnalysis::roomId)
				.containsExactly(hallway, bedroom);
	}

	@Test
	@DisplayName("the ceiling vocabulary V9 wrote is refused at the row, not only in the schema file")
	void enforcesTheCeilingVocabulary() {
		UUID room = aRoomOf(RoomType.BEDROOM, 0);
		analyses.save(analysis(room, List.of(surface("WALL_1", "0.900"))));

		assertThatThrownBy(() -> jdbc.update(
				"UPDATE room_analysis SET ceiling_staining = 'HEAVY' WHERE room_id = ?", room))
				.hasMessageContaining("room_analysis_ceiling_staining_check");
	}

	// -----------------------------------------------------------------------------------------------

	private RoomAnalysis analysis(UUID roomId, List<SurfaceFinding> surfaces) {
		return new RoomAnalysis(roomId, "v1", "test-model", "{\"roomType\":\"BEDROOM\"}",
				surfaces,
				new CeilingFinding(Moisture.STAIN, FillerBand.LOW), new BigDecimal("0.700"),
				true, 6, Furnishing.FURNISHED, 2, 3, 1, new BigDecimal("0.830"),
				List.of("WALL_3"), List.of("sol duvarda priz hizasında çatlak"));
	}

	private static SurfaceFinding surface(String id, String confidence) {
		return new SurfaceFinding(id, Coating.PAINTED, Tone.LIGHT, FillerBand.NONE, false,
				CrackLevel.NONE, Moisture.NONE, false, new BigDecimal(confidence));
	}

	/** A room of a saved request, at the given position in the capture order. */
	private UUID aRoomOf(RoomType type, int sortOrder) {
		if (quoteRequestId == null) {
			QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
					"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
					QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 3, false, WallCondition.MINOR, null));
			requests.save(draft);
			quoteRequestId = draft.id();
			existing = List.of();
		}
		UUID id = Uuid7.generate();
		existing = concat(existing, new ConfirmedRoom(id, type, type.name() + " " + sortOrder,
				sortOrder, List.of(PhotoRole.WALL_1, PhotoRole.CEILING), true));
		rooms.replaceAll(quoteRequestId, new ConfirmedRooms(existing));
		return id;
	}

	private List<ConfirmedRoom> existing = List.of();

	private static List<ConfirmedRoom> concat(List<ConfirmedRoom> rooms, ConfirmedRoom added) {
		return java.util.stream.Stream.concat(rooms.stream(), java.util.stream.Stream.of(added)).toList();
	}
}
