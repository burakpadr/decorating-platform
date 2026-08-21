package com.burakpadr.decorating.quoting.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierTarget;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.PriceModifier;
import com.burakpadr.decorating.quoting.domain.model.PricedQuote;
import com.burakpadr.decorating.quoting.domain.model.PricingInput;
import com.burakpadr.decorating.quoting.domain.model.PricingSource;
import com.burakpadr.decorating.quoting.domain.model.RoomInput;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.RoomTypeConfig;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import com.burakpadr.decorating.quoting.domain.service.PricingEngine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The adapter that turns price book rows into the value the engine is handed (§2 outbound ports,
 * §4.5).
 *
 * <p>Against a real Postgres rather than a mock, because everything that can go wrong here is a
 * database detail: a jsonb column that arrives as the wrong type, a numeric that loses its scale, a
 * Turkish district name mangled by an encoding, an enum value the code does not know. None of those
 * fail against an in-memory stub.
 *
 * <p>The rows come from the migrations, so this also asserts that V2 and V3 are loadable by the code
 * that has to read them — the seed and the reader drifting apart is exactly the kind of break that
 * only shows up at startup in production.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PriceBookPersistenceAdapterTest {

	private static final String ACTIVE = "REAL-2026-03";
	private static final String SUPERSEDED = "SEED-2026-01";

	@Autowired
	private PriceBookRepository repository;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("the active version loads with everything the engine looks up")
	void loadsTheActiveVersionInFull() {
		PriceBook book = repository.findActive().orElseThrow();

		assertThat(book.versionCode()).isEqualTo(ACTIVE);
		assertThat(book.items()).hasSize(14);
		assertThat(book.modifiers()).hasSize(4);
		assertThat(book.roomTypes()).hasSize(8);
		assertThat(book.districts()).hasSize(39);

		assertThat(book.ceilingHeightM()).isEqualByComparingTo("2.70");
		assertThat(book.grossToNetRatio()).isEqualByComparingTo("0.82");
		assertThat(book.stage1OpeningRatio()).isEqualByComparingTo("0.12");
		assertThat(book.doorOpeningM2()).isEqualByComparingTo("1.90");
		assertThat(book.windowOpeningM2()).isEqualByComparingTo("2.20");
		assertThat(book.crewSize())
				.as("two painters go to a job (V6), and the same person-day still costs 2,500")
				.isEqualTo(2);
		assertThat(book.crewHoursPerDay()).isEqualByComparingTo("8.00");
		assertThat(book.crewDayCost()).isEqualByComparingTo("5000.00");
		assertThat(book.dayRoundingTolerance()).isEqualByComparingTo("0.25");
		assertThat(book.marginRatio()).isEqualByComparingTo("0.30");
		assertThat(book.marginAlertThreshold()).isEqualByComparingTo("0.20");
		assertThat(book.baseBandRatio()).isEqualByComparingTo("0.12");
		assertThat(book.labourVatRate())
				.as("still the placeholder both rates carry until the accountant answers (BOYA-3)")
				.isEqualByComparingTo("0.2000");
		assertThat(book.materialVatRate()).isEqualByComparingTo("0.2000");
	}

	@Test
	@DisplayName("an item keeps its costs and its person-minutes, at full scale")
	void mapsItemCostsAndMinutes() {
		PriceBook book = repository.findActive().orElseThrow();

		assertThat(book.item(ItemCode.WALL_PAINT).labourCost()).isEqualByComparingTo("31.25");
		assertThat(book.item(ItemCode.WALL_PAINT).materialCost()).isEqualByComparingTo("22.00");
		assertThat(book.item(ItemCode.WALL_PAINT).labourMinutes()).isEqualByComparingTo("6.00");
		// V5 split mobilization: 60 minutes of crew time, and the van and fuel as material (ADR 0016).
		assertThat(book.item(ItemCode.MOBILIZATION).labourCost()).isEqualByComparingTo("312.50");
		assertThat(book.item(ItemCode.MOBILIZATION).materialCost()).isEqualByComparingTo("1587.50");
		assertThat(book.item(ItemCode.CORNICE_CUTTING).labourMinutes()).isEqualByComparingTo("45.00");
	}

	@Test
	@DisplayName("a modifier keeps its target, and its jsonb scope becomes item codes")
	void mapsModifierTargetsAndScopes() {
		PriceBook book = repository.findActive().orElseThrow();

		PriceModifier furnished = book.modifiers().get(ModifierCode.FURNISHED);
		assertThat(furnished.factor()).isEqualByComparingTo("1.2500");
		assertThat(furnished.target()).isEqualTo(ModifierTarget.LABOUR);
		assertThat(furnished.scopeItems())
				.as("a null scope_items column means every item, not no item")
				.isEmpty();

		PriceModifier darkToLight = book.modifiers().get(ModifierCode.DARK_TO_LIGHT);
		assertThat(darkToLight.target()).isEqualTo(ModifierTarget.BOTH);
		assertThat(darkToLight.scopeItems()).containsExactlyInAnyOrder(ItemCode.WALL_PAINT, ItemCode.DOOR_PAINT);

		assertThat(book.modifiers().get(ModifierCode.NO_ELEVATOR).scopeItems())
				.containsExactly(ItemCode.MOBILIZATION);
	}

	@Test
	@DisplayName("room type coefficients survive the round trip, decimals included")
	void mapsRoomTypeCoefficients() {
		PriceBook book = repository.findActive().orElseThrow();

		RoomTypeConfig kitchen = book.roomType(RoomType.KITCHEN);
		assertThat(kitchen.areaWeight()).isEqualByComparingTo("1.10");
		assertThat(kitchen.perimeterFactor()).isEqualByComparingTo("4.30");
		assertThat(kitchen.paintableRatio())
				.as("a tiled kitchen is mostly not paintable, and 0.65 is the difference between a "
						+ "right quote and a third too much")
				.isEqualByComparingTo("0.6500");
		assertThat(book.roomType(RoomType.HALLWAY).perimeterFactor()).isEqualByComparingTo("5.50");
	}

	@Test
	@DisplayName("the frames each room type asks for come out of the jsonb column, in order")
	void mapsTheRequiredPhotos() {
		PriceBook book = repository.findActive().orElseThrow();

		assertThat(book.roomType(RoomType.LIVING_ROOM).requiredPhotos())
				.containsExactly(PhotoRole.WALL_1, PhotoRole.WALL_2, PhotoRole.WALL_3, PhotoRole.WALL_4,
						PhotoRole.CEILING);
		assertThat(book.roomType(RoomType.BATHROOM).requiredPhotos())
				.as("order is shooting order, so it is not incidental")
				.containsExactly(PhotoRole.WALL_1, PhotoRole.CEILING);
		assertThat(book.roomType(RoomType.KITCHEN).requiredPhotos()).hasSize(3);
	}

	@Test
	@DisplayName("districts keep their Turkish names and their factors")
	void mapsDistrictsWithTheirFactors() {
		PriceBook book = repository.findActive().orElseThrow();

		assertThat(book.districts().get("KADIKOY").displayName())
				.as("the one Turkish string in the pricing domain; an encoding slip shows up here")
				.isEqualTo("Kadıköy");
		assertThat(book.districts().get("KADIKOY").active()).isTrue();
		assertThat(book.districtFactor("KADIKOY"))
				.as("still 1.0000 everywhere — district differences are a business input (§16)")
				.isEqualByComparingTo("1.0000");
		assertThat(book.districtFactor("CORLU"))
				.as("a district the version does not carry prices at 1.0000 rather than failing")
				.isEqualByComparingTo("1.0000");
	}

	@Test
	@DisplayName("a superseded version still loads, with its own items")
	void findsASupersededVersionByItsCode() {
		PriceBook seed = repository.findByVersionCode(SUPERSEDED).orElseThrow();

		assertThat(seed.versionCode()).isEqualTo(SUPERSEDED);
		assertThat(seed.items()).hasSize(14);
		assertThat(seed.item(ItemCode.WALL_PAINT).labourCost())
				.as("a quote priced against the seed must still read the seed's figures")
				.isEqualByComparingTo("62.00");
	}

	@Test
	@DisplayName("an unknown version code is empty, not an exception")
	void anUnknownVersionCodeIsEmpty() {
		Optional<PriceBook> missing = repository.findByVersionCode("NO-SUCH-VERSION");

		assertThat(missing).isEmpty();
	}

	@Test
	@DisplayName("the loaded version is complete enough to price a quote end to end")
	void theLoadedVersionCanPriceAQuote() {
		PriceBook book = repository.findActive().orElseThrow();

		PricedQuote quote = new PricingEngine().price(new PricingInput(
				"KADIKOY", new BigDecimal("92.00"), false,
				List.of(RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.MINOR),
						RoomInput.declared(RoomType.MASTER_BEDROOM, WallCondition.MINOR),
						RoomInput.declared(RoomType.BEDROOM, WallCondition.MINOR),
						RoomInput.declared(RoomType.BEDROOM, WallCondition.MINOR),
						RoomInput.declared(RoomType.KITCHEN, WallCondition.MINOR),
						RoomInput.declared(RoomType.BATHROOM, WallCondition.MINOR),
						RoomInput.declared(RoomType.HALLWAY, WallCondition.MINOR)),
				Furnishing.FURNISHED, 8, true, false, true, false, PricingSource.STAGE_1), book);

		// §5.10's shape, priced against the reconciled book (V5) at Kadıköy's 1.0000 district factor. Not
		// comparable to §5.10's 52,509.86: that figure belongs to the seed's item costs, which
		// PricingEngineTest still asserts against the fixture. Here the point is that the version the
		// database hands back prices a whole job without a missing row — 4,176.85 person-minutes over a
		// three-person crew is 2.90 days, so the 22,500 TL floor does not bind.
		// The cost is V5's to the kuruş: labour is derived per person-minute, so spreading the same work
		// over two people instead of three changes how long it takes and not what it costs (V6).
		assertThat(quote.totalCost()).isEqualByComparingTo("32955.51");
		assertThat(quote.billableDays())
				.as("4,176.85 person-minutes over two people is 4.35 days, and §5.8 rounds it to 5")
				.isEqualTo(5);
		assertThat(quote.lines()).extracting(line -> line.code())
				.contains(ItemCode.WALL_PAINT, ItemCode.CEILING_PAINT, ItemCode.PATCH_FILLING,
						ItemCode.DOOR_PAINT, ItemCode.MASKING, ItemCode.MOBILIZATION);
	}

	@Test
	@DisplayName("one version's figures cannot leak into another's")
	void versionsDoNotLeakIntoEachOther() {
		// A decoy version with a figure nothing else has. Before V5 this was the only way to prove
		// isolation at all, because REAL-2026-01 and SEED-2026-01 carried identical item costs and a read
		// that ignored price_book_id collapsed onto the same numbers and looked correct. The two differ
		// now, but the decoy stays: it is the only assertion here that fails for the right reason.
		UUID decoy = UUID.randomUUID();
		jdbc.update("INSERT INTO price_book (id, version_code, active, crew_day_cost, labour_vat_rate, "
				+ "material_vat_rate) VALUES (?, 'DECOY-9999', false, 9999.00, 0.2000, 0.2000)", decoy);
		jdbc.update("INSERT INTO price_book_item (id, price_book_id, code, unit, labour_cost, "
				+ "material_cost, labour_minutes) VALUES (?, ?, 'WALL_PAINT', 'SQM', 999.99, 1.00, 1.00)",
				UUID.randomUUID(), decoy);
		try {
			assertThat(repository.findActive().orElseThrow().item(ItemCode.WALL_PAINT).labourCost())
					.as("the active version must not take a figure from a version nobody activated")
					.isEqualByComparingTo("31.25");
			assertThat(repository.findByVersionCode("DECOY-9999").orElseThrow()
							.item(ItemCode.WALL_PAINT).labourCost())
					.isEqualByComparingTo("999.99");
			assertThat(repository.findByVersionCode("DECOY-9999").orElseThrow().items())
					.as("and it must not inherit the rest of the active version's list either")
					.hasSize(1);
		} finally {
			jdbc.update("DELETE FROM price_book WHERE id = ?", decoy);
		}
	}

	@Test
	@DisplayName("the database refuses a second active version — the partial unique index holds")
	void onlyOneVersionCanBeActive() {
		assertThatThrownBy(() -> jdbc.update(
						"UPDATE price_book SET active = true WHERE version_code = ?", SUPERSEDED))
				.as("two active books would make 'the price list' ambiguous, and nothing reads by name")
				.hasMessageContaining("price_book_single_active_idx");

		Integer active = jdbc.queryForObject(
				"SELECT count(*) FROM price_book WHERE active = true", Integer.class);
		assertThat(active).isEqualTo(1);
	}
}
