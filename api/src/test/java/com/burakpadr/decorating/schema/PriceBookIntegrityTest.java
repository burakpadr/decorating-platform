package com.burakpadr.decorating.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guards the price book across the Phase 0 handover (spec §15), where the seed's market-derived
 * placeholders are replaced by the business's real costs.
 *
 * <p>Two distinct failures are in scope, and neither is caught by anything else in the build:
 *
 * <ol>
 *   <li>A new price book version silently drops an item code. Every code in §5.6 is a quantity the
 *       engine will look up; a missing row is a line item priced at nothing, which is money lost on
 *       every quote that touches it and produces no visible symptom.
 *   <li>The migration and §5.11 drift apart. They are deliberately duplicated — the table is the
 *       specification, the migration is the fact — so the build has to be what notices, following
 *       the pattern set by {@code web-ui/app/utils/districts.spec.ts}.
 * </ol>
 *
 * <p>Read-only: this asserts what the migrations produced and writes nothing.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PriceBookIntegrityTest {

	/** Every item code §5.6 maps a quantity onto. The engine cannot price a job without all of them. */
	private static final List<String> ITEM_CODES_REQUIRED_BY_THE_ENGINE = List.of(
			"WALL_PAINT",
			"CEILING_PAINT",
			"PATCH_FILLING",
			"SKIM_COAT",
			"PRIMER",
			"STAIN_BLOCK_PRIMER",
			"WALLPAPER_STRIPPING",
			"DOOR_PAINT",
			"TRIM_PAINT",
			"RADIATOR_PAINT",
			"DOWNLIGHT_CUTTING",
			"CORNICE_CUTTING",
			"MASKING",
			"MOBILIZATION");

	private static final String SEED_VERSION = "SEED-2026-01";

	/** The §5.11 table, transcribed: code → {labour_cost, material_cost, labour_minutes}. */
	private static final Map<String, String[]> SEED_ITEMS_PER_SPEC = seedItems();

	private static Map<String, String[]> seedItems() {
		Map<String, String[]> items = new LinkedHashMap<>();
		items.put("WALL_PAINT", new String[] {"SQM", "62.00", "38.00", "6.00"});
		items.put("CEILING_PAINT", new String[] {"SQM", "70.00", "38.00", "8.00"});
		items.put("PATCH_FILLING", new String[] {"SQM", "50.00", "15.00", "12.00"});
		items.put("SKIM_COAT", new String[] {"SQM", "100.00", "42.00", "22.00"});
		items.put("PRIMER", new String[] {"SQM", "20.00", "15.00", "3.00"});
		items.put("STAIN_BLOCK_PRIMER", new String[] {"SQM", "25.00", "40.00", "4.00"});
		items.put("WALLPAPER_STRIPPING", new String[] {"SQM", "48.00", "2.00", "14.00"});
		items.put("DOOR_PAINT", new String[] {"UNIT", "350.00", "150.00", "55.00"});
		items.put("TRIM_PAINT", new String[] {"UNIT", "140.00", "52.00", "22.00"});
		items.put("RADIATOR_PAINT", new String[] {"UNIT", "270.00", "115.00", "40.00"});
		items.put("DOWNLIGHT_CUTTING", new String[] {"UNIT", "46.00", "0.00", "8.00"});
		items.put("CORNICE_CUTTING", new String[] {"ROOM", "308.00", "0.00", "45.00"});
		items.put("MASKING", new String[] {"ROOM", "115.00", "62.00", "25.00"});
		items.put("MOBILIZATION", new String[] {"LUMP_SUM", "1900.00", "0.00", "60.00"});
		return items;
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("BOYA-1 gate: the active price book is no longer the market-derived seed")
	void activePriceBookIsNotThePlaceholderSeed() {
		String activeVersion = jdbc.queryForObject(
				"SELECT version_code FROM price_book WHERE active = true", String.class);

		assertThat(activeVersion)
				.as("Phase 0 is incomplete while the placeholder seed is still the active price book")
				.isNotEqualTo(SEED_VERSION);
	}

	@Test
	@DisplayName("the active price book carries every item code the pricing engine looks up")
	void activePriceBookIsComplete() {
		List<String> codes = jdbc.queryForList(
				"SELECT i.code FROM price_book_item i "
						+ "JOIN price_book b ON b.id = i.price_book_id WHERE b.active = true",
				String.class);

		assertThat(codes).containsExactlyInAnyOrderElementsOf(ITEM_CODES_REQUIRED_BY_THE_ENGINE);
	}

	@Test
	@DisplayName("exactly one price book is active")
	void exactlyOnePriceBookIsActive() {
		Integer active = jdbc.queryForObject(
				"SELECT count(*) FROM price_book WHERE active = true", Integer.class);

		assertThat(active).isEqualTo(1);
	}

	@Test
	@DisplayName("the seed price book still matches the §5.11 table it was transcribed from")
	void seedPriceBookMatchesTheSpecTable() {
		SEED_ITEMS_PER_SPEC.forEach((code, expected) -> {
			Map<String, Object> row = jdbc.queryForMap(
					"SELECT i.unit, i.labour_cost, i.material_cost, i.labour_minutes "
							+ "FROM price_book_item i JOIN price_book b ON b.id = i.price_book_id "
							+ "WHERE b.version_code = ? AND i.code = ?",
					SEED_VERSION, code);

			assertThat(row.get("unit")).as("%s unit", code).isEqualTo(expected[0]);
			assertThat((BigDecimal) row.get("labour_cost"))
					.as("%s labour_cost", code)
					.isEqualByComparingTo(expected[1]);
			assertThat((BigDecimal) row.get("material_cost"))
					.as("%s material_cost", code)
					.isEqualByComparingTo(expected[2]);
			assertThat((BigDecimal) row.get("labour_minutes"))
					.as("%s labour_minutes", code)
					.isEqualByComparingTo(expected[3]);
		});
	}

	@Test
	@DisplayName("the seed crew figures still match §5.11: 3 people, 8 h/day, 4,500 TL/day")
	void seedCrewFiguresMatchTheSpec() {
		Map<String, Object> book = jdbc.queryForMap(
				"SELECT crew_size, crew_hours_per_day, crew_day_cost FROM price_book WHERE version_code = ?",
				SEED_VERSION);

		assertThat(book.get("crew_size")).isEqualTo(3);
		assertThat((BigDecimal) book.get("crew_hours_per_day")).isEqualByComparingTo("8.00");
		assertThat((BigDecimal) book.get("crew_day_cost")).isEqualByComparingTo("4500.00");
	}

	@Test
	@DisplayName("the active price book is self-contained: modifiers, room types and districts")
	void activePriceBookIsSelfContained() {
		assertThat(activeBookColumn("SELECT code FROM price_modifier WHERE price_book_id = ?"))
				.as("modifiers")
				.containsExactlyInAnyOrderElementsOf(
						seedColumn("SELECT code FROM price_modifier WHERE price_book_id = ?"));

		assertThat(activeBookColumn("SELECT room_type FROM room_type_config WHERE price_book_id = ?"))
				.as("room types")
				.containsExactlyInAnyOrderElementsOf(
						seedColumn("SELECT room_type FROM room_type_config WHERE price_book_id = ?"));

		assertThat(activeBookColumn("SELECT district_code FROM service_district WHERE price_book_id = ?"))
				.as("service districts")
				.containsExactlyInAnyOrderElementsOf(
						seedColumn("SELECT district_code FROM service_district WHERE price_book_id = ?"));
	}

	@Test
	@DisplayName("no active item is free to do: labour cost and duration are both above zero")
	void activeItemsHaveLabourCostAndDuration() {
		List<String> free = jdbc.queryForList(
				"SELECT i.code FROM price_book_item i JOIN price_book b ON b.id = i.price_book_id "
						+ "WHERE b.active = true AND (i.labour_cost <= 0 OR i.labour_minutes <= 0 "
						+ "OR i.material_cost < 0)",
				String.class);

		assertThat(free)
				.as("an item priced at zero labour silently drops out of both the cost and the duration")
				.isEmpty();
	}

	private List<String> activeBookColumn(String sql) {
		UUID activeId = jdbc.queryForObject(
				"SELECT id FROM price_book WHERE active = true", UUID.class);
		return jdbc.queryForList(sql, String.class, activeId);
	}

	private List<String> seedColumn(String sql) {
		UUID seedId = jdbc.queryForObject(
				"SELECT id FROM price_book WHERE version_code = ?", UUID.class, SEED_VERSION);
		return jdbc.queryForList(sql, String.class, seedId);
	}
}
