package com.burakpadr.decorating.quoting.application;

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
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.Quote;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.QuoteState;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.SurfaceFinding;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.model.IncreaseTarget;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.in.GenerateQuote;
import com.burakpadr.decorating.quoting.domain.port.in.ManagePriceBookVersions;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomAnalysisRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
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
 * Pricing a request from its findings (§5.1, §5.5, workflow §4.2, BOYA-50).
 *
 * <p>Against the database because the deliverable is rows: §4.6's {@code quote} and its line items,
 * including the two columns that only exist to be read six months later — {@code applied_modifiers},
 * which answers "why does this line carry a factor", and {@code labour_minutes}, which answers "which
 * line makes this a three-day job".
 *
 * <p>The arithmetic itself is {@code PricingEngineTest}'s, with 56 cases and §5.10 as a fixture.
 * What is asserted here is that the findings reach the engine as §5.1 says they must, and that
 * everything the engine produced survives into the row.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GenerateQuoteTest {

	@Autowired
	private GenerateQuote generateQuote;

	@Autowired
	private RoomAnalysisRepository analyses;

	@Autowired
	private RoomRepository rooms;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ManagePriceBookVersions priceBooks;

	@Autowired
	private EstimateStageOne estimateStageOne;

	private UUID quoteRequestId;
	private final List<ConfirmedRoom> confirmed = new ArrayList<>();

	@BeforeEach
	void aFurnishedThreePlusOneInKadikoy() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, WallCondition.MINOR, null));
		requests.save(draft);
		quoteRequestId = draft.id();
		// The customer flow prices before it asks for photographs, and that estimate is what binds the
		// request to a price book version (§4.2). Done here so every test below starts where a real
		// request does.
		estimateStageOne.estimate(quoteRequestId);
	}

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
		// A version one of these tests activated would otherwise outlive it and reprice the next one.
		jdbc.update("DELETE FROM price_book WHERE version_code NOT IN "
				+ "('REAL-2026-01', 'REAL-2026-02', 'REAL-2026-03')");
		jdbc.update("UPDATE price_book SET active = false WHERE active = true");
		jdbc.update("UPDATE price_book SET active = true WHERE version_code = 'REAL-2026-03'");
	}

	@Test
	@DisplayName("findings become a DRAFT quote and its line items")
	void pricesTheFindings() {
		UUID lounge = area(RoomType.LIVING_ROOM);
		UUID bedroom = area(RoomType.BEDROOM);
		analyse(lounge, painted("WALL_1", Tone.DARK, FillerBand.MEDIUM));
		analyse(bedroom, painted("WALL_1", Tone.LIGHT, FillerBand.LOW));

		Quote quote = generateQuote.generate(quoteRequestId);

		assertThat(quote.state()).isEqualTo(QuoteState.DRAFT);
		assertThat(quote.revision()).isEqualTo(1);
		assertThat(quote.priced().total()).isPositive();
		assertThat(quote.priced().totalWallSqm()).isPositive();

		assertThat(jdbc.queryForObject("""
				SELECT status FROM quote WHERE quote_request_id = ?
				""", String.class, quoteRequestId)).isEqualTo("DRAFT");
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM quote_line_item l JOIN quote q ON q.id = l.quote_id
				WHERE q.quote_request_id = ?
				""", Integer.class, quoteRequestId)).isEqualTo(quote.priced().lines().size());
	}

	@Test
	@DisplayName("the row keeps the figures the engine produced, to the kuruş")
	void storesEveryFigure() {
		analyse(area(RoomType.BEDROOM), painted("WALL_1", Tone.LIGHT, FillerBand.NONE));

		Quote quote = generateQuote.generate(quoteRequestId);

		var row = jdbc.queryForMap(
				"SELECT * FROM quote WHERE quote_request_id = ?", quoteRequestId);
		assertThat((BigDecimal) row.get("total")).isEqualByComparingTo(quote.priced().total());
		assertThat((BigDecimal) row.get("total_cost")).isEqualByComparingTo(quote.priced().totalCost());
		assertThat((BigDecimal) row.get("band_low")).isEqualByComparingTo(quote.priced().bandLow());
		assertThat((BigDecimal) row.get("band_high")).isEqualByComparingTo(quote.priced().bandHigh());
		assertThat((BigDecimal) row.get("total_wall_sqm"))
				.isEqualByComparingTo(quote.priced().totalWallSqm());
		assertThat(row.get("estimated_days")).isEqualTo(quote.priced().billableDays());
		assertThat(row.get("created_by")).isEqualTo("SYSTEM");
	}

	@Test
	@DisplayName("each line says which modifiers moved it and how long it takes")
	void storesTheAuditColumns() {
		// §4.6's reason for both columns: "why does this line have a 1.5 factor" and which line drives
		// the day count. The home is furnished, so every labour-bearing line carries FURNISHED.
		analyse(area(RoomType.BEDROOM), painted("WALL_1", Tone.LIGHT, FillerBand.NONE));

		generateQuote.generate(quoteRequestId);

		assertThat(jdbc.queryForObject("""
				SELECT applied_modifiers::text FROM quote_line_item l JOIN quote q ON q.id = l.quote_id
				WHERE q.quote_request_id = ? AND l.item_code = ?
				""", String.class, quoteRequestId, ItemCode.WALL_PAINT.name()))
				.contains("FURNISHED").contains("\"labour\": 1.2500").contains("\"material\": 1.0000");

		assertThat(jdbc.queryForObject("""
				SELECT labour_minutes FROM quote_line_item l JOIN quote q ON q.id = l.quote_id
				WHERE q.quote_request_id = ? AND l.item_code = ?
				""", BigDecimal.class, quoteRequestId, ItemCode.WALL_PAINT.name())).isPositive();
	}

	@Test
	@DisplayName("a tiled wall is not a cheaper wall — it is not painted at all")
	void excludesUnpaintableSurfaces() {
		// §5.5, and the difference stage 2 exists for: stage 1 applies a flat coating ratio, while a
		// finding says which surfaces are paint and which are tile. The engine reads coating from
		// surface_finding now, not from an assumption.
		UUID tiled = area(RoomType.BATHROOM);
		analyse(tiled, new SurfaceFinding("ROOM_GENERAL", Coating.TILE, Tone.LIGHT, FillerBand.NONE,
				false, CrackLevel.NONE, Moisture.NONE, false, new BigDecimal("0.900")));

		Quote quote = generateQuote.generate(quoteRequestId);

		assertThat(quote.priced().totalWallSqm()).isEqualByComparingTo("0.00");
		assertThat(quote.priced().hasLine(ItemCode.WALL_PAINT)).isFalse();
		// The ceiling is still there: a tiled wall says nothing about what is overhead (ADR 0017).
		assertThat(quote.priced().totalCeilingSqm()).isPositive();
	}

	@Test
	@DisplayName("a home with one room unanalysed is refused, and the message names the room")
	void refusesAPartiallyAnalysedHome() {
		// Priced anyway, the quote would be for less work than the job needs and the shortfall would
		// look like every other total. "Could not price" alone sends somebody to read seven rows.
		UUID lounge = area(RoomType.LIVING_ROOM);
		area(RoomType.BEDROOM);
		analyse(lounge, painted("WALL_1", Tone.LIGHT, FillerBand.NONE));

		assertThatThrownBy(() -> generateQuote.generate(quoteRequestId))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Yatak odası")
				.hasMessageContaining("has no analysis");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM quote", Integer.class)).isZero();
	}

	@Test
	@DisplayName("a second pricing is a new revision, and the first is superseded")
	void supersedesThePreviousRevision() {
		// What a recapture produces: new findings, a new quote. The old one is not deleted, because a
		// customer may be holding it.
		UUID bedroom = area(RoomType.BEDROOM);
		analyse(bedroom, painted("WALL_1", Tone.LIGHT, FillerBand.NONE));
		generateQuote.generate(quoteRequestId);

		analyse(bedroom, painted("WALL_1", Tone.DARK, FillerBand.FULL));
		Quote second = generateQuote.generate(quoteRequestId);

		assertThat(second.revision()).isEqualTo(2);
		assertThat(jdbc.queryForList("""
				SELECT status FROM quote WHERE quote_request_id = ? ORDER BY revision
				""", String.class, quoteRequestId)).containsExactly("SUPERSEDED", "DRAFT");
	}

	@Test
	@DisplayName("priced against the version that priced the estimate, not against a zam that landed after")
	void pricesAgainstTheVersionTheRequestIsBoundTo() {
		// The customer carried on from a range. A quote computed against a version activated in between
		// answers a different question than the one they were shown, and neither figure would say so.
		// Same reason `room` reads its required frames from the request's version (BOYA-37).
		UUID bedroom = area(RoomType.BEDROOM);
		analyse(bedroom, painted("WALL_1", Tone.LIGHT, FillerBand.NONE));
		String bound = jdbc.queryForObject("""
				SELECT b.version_code FROM price_book b
				JOIN quote_request q ON q.price_book_id = b.id WHERE q.id = ?
				""", String.class, quoteRequestId);

		UUID dearer = priceBooks.applyBulkIncrease(
				priceBooks.list().stream().filter(PriceBookSummary::active).findFirst().orElseThrow().id(),
				IncreaseTarget.ALL, new BigDecimal("40")).id();
		priceBooks.activate(dearer);

		Quote quote = generateQuote.generate(quoteRequestId);

		assertThat(quote.priceBookVersion()).isEqualTo(bound);
	}

	// -----------------------------------------------------------------------------------------------

	private static SurfaceFinding painted(String id, Tone tone, FillerBand filler) {
		return new SurfaceFinding(id, Coating.PAINTED, tone, filler, false,
				CrackLevel.NONE, Moisture.NONE, false, new BigDecimal("0.900"));
	}

	private UUID area(RoomType type) {
		UUID id = Uuid7.generate();
		confirmed.add(new ConfirmedRoom(id, type, label(type), confirmed.size(),
				List.of(PhotoRole.WALL_1, PhotoRole.CEILING), true));
		rooms.replaceAll(quoteRequestId, new ConfirmedRooms(confirmed));
		return id;
	}

	private static String label(RoomType type) {
		return type == RoomType.BEDROOM ? "Yatak odası" : type.name();
	}

	private void analyse(UUID roomId, SurfaceFinding... surfaces) {
		analyses.save(new RoomAnalysis(roomId, "v1", "test-model", "{}", List.of(surfaces),
				CeilingFinding.none(), new BigDecimal("0.850"), false, 0,
				Furnishing.FURNISHED, 1, 1, 1, new BigDecimal("0.880"), List.of(), List.of()));
	}
}
