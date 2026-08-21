package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.DuplicateVersionCode;
import com.burakpadr.decorating.quoting.domain.model.IncreaseTarget;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionLocked;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionNotFound;
import com.burakpadr.decorating.quoting.domain.port.in.ManagePriceBookVersions;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Managing price book versions (§7, workflow §6): the quarterly increase, and the promise that comes
 * with it.
 *
 * <p>The promise is the reason this test exists. "Eski teklifler değişmez" — a customer who turns up
 * with a two-week-old quote must be holding a figure the system can still explain. That works only
 * because a quote records the version it was priced with and versions are never edited, so the test
 * that matters here is not "does activation work" but "did anything already sent move".
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PriceBookVersionManagementTest {

	/** The reconciled book (V5). Its items agree with its own crew rate, and the tests below rely on it. */
	private static final String ACTIVE = "REAL-2026-03";

	/**
	 * WALL_PAINT at 6 person-minutes, priced at REAL-2026-03's 5,000 TL crew day over two people:
	 * 6 × 5000 / 960. The same 31.25 REAL-2026-02 carried at 7,500 over three — a person-minute costs
	 * what it costs, whoever is in the van (V6).
	 */
	private static final String WALL_PAINT_LABOUR = "31.25";

	@Autowired
	private ManagePriceBookVersions versions;

	@Autowired
	private PriceBookRepository books;

	@Autowired
	private JdbcTemplate jdbc;

	/** The database is shared with every other test in the suite; leave it as it was found. */
	@org.junit.jupiter.api.AfterEach
	void restoreTheActiveVersion() {
		jdbc.update("DELETE FROM quote WHERE created_by = 'OPERATOR'");
		jdbc.update("DELETE FROM quote_request WHERE status = 'QUOTE_SENT'");
		jdbc.update("DELETE FROM price_book WHERE version_code LIKE 'TEST-%'");
		// REAL-2026-02 and -03 are migrations, not fixtures: only the versions these tests produce go.
		jdbc.update("DELETE FROM price_book WHERE version_code IN ('REAL-2026-04', 'REAL-2026-05')");
		jdbc.update("UPDATE price_book SET active = false WHERE active = true");
		jdbc.update("UPDATE price_book SET active = true WHERE version_code = ?", ACTIVE);
	}

	@Test
	@DisplayName("every version is listed, and exactly one of them is active")
	void listsEveryVersion() {
		List<PriceBookSummary> all = versions.list();

		assertThat(all).extracting(PriceBookSummary::versionCode).contains(ACTIVE, "SEED-2026-01");
		assertThat(all.stream().filter(PriceBookSummary::active).count()).isEqualTo(1);
	}

	@Test
	@DisplayName("a new version is a full copy of its source, and starts switched off")
	void aNewVersionIsAFullCopyAndStartsInactive() {
		UUID source = idOf(ACTIVE);

		PriceBookSummary created = versions.createVersionFrom(source, "TEST-COPY-1");

		assertThat(created.active())
				.as("a version is reviewed before it prices anything, so it cannot arrive active")
				.isFalse();
		assertThat(created.id()).isNotEqualTo(source);
		assertThat(childRows("price_book_item", created.id())).isEqualTo(14);
		assertThat(childRows("price_modifier", created.id())).isEqualTo(4);
		assertThat(childRows("room_type_config", created.id())).isEqualTo(8);
		assertThat(childRows("service_district", created.id())).isEqualTo(39);
		assertThat(books.findByVersionCode("TEST-COPY-1").orElseThrow()
						.item(ItemCode.WALL_PAINT).labourCost())
				.as("a copy that priced differently from its source would be an edit, not a copy")
				.isEqualByComparingTo(WALL_PAINT_LABOUR);
	}

	@Test
	@DisplayName("activating a version switches the previous one off, never both on")
	void activatingLeavesExactlyOneActive() {
		PriceBookSummary created = versions.createVersionFrom(idOf(ACTIVE), "TEST-COPY-2");

		versions.activate(created.id());

		assertThat(books.findActive().orElseThrow().versionCode()).isEqualTo("TEST-COPY-2");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM price_book WHERE active = true", Integer.class))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject(
						"SELECT active FROM price_book WHERE version_code = ?", Boolean.class, ACTIVE))
				.isFalse();
	}

	@Test
	@DisplayName("a quote already sent keeps the figures it was computed with")
	void aQuoteAlreadySentKeepsItsFigures() {
		UUID oldVersion = idOf(ACTIVE);
		UUID quote = insertSentQuote(oldVersion);

		PriceBookSummary raised = versions.createVersionFrom(oldVersion, "TEST-RAISE-1");
		jdbc.update("UPDATE price_book_item SET labour_cost = 99.00 "
				+ "WHERE price_book_id = ? AND code = 'WALL_PAINT'", raised.id());
		versions.activate(raised.id());

		assertThat(jdbc.queryForObject(
						"SELECT price_book_id FROM quote WHERE id = ?", UUID.class, quote))
				.as("the quote points at the version that priced it, and that pointer never moves")
				.isEqualTo(oldVersion);
		assertThat(books.findByVersionCode(ACTIVE).orElseThrow().item(ItemCode.WALL_PAINT).labourCost())
				.as("the superseded version is still readable, unchanged — that is what makes the "
						+ "conversation with a customer holding an old quote possible")
				.isEqualByComparingTo(WALL_PAINT_LABOUR);
		assertThat(books.findActive().orElseThrow().item(ItemCode.WALL_PAINT).labourCost())
				.as("while new quotes price at the raised figure")
				.isEqualByComparingTo("99.00");
	}

	@Test
	@DisplayName("a labour increase raises the crew rate, and every item follows it")
	void raisesLabourOnly() {
		PriceBookSummary raised = versions.applyBulkIncrease(
				idOf(ACTIVE), IncreaseTarget.LABOUR, new BigDecimal("15"));

		PriceBook before = books.findByVersionCode(ACTIVE).orElseThrow();
		PriceBook after = books.findByVersionCode(raised.versionCode()).orElseThrow();

		// Labour got 15% dearer because the crew did, which is the only way labour gets dearer
		// (ADR 0016). 7,500 → 8,625, and WALL_PAINT's 6 minutes are worth 6 × 8625 / 1440.
		assertThat(after.crewDayCost())
				.as("the rise lands on the figure that explains it")
				.isEqualByComparingTo("5750.00");
		assertThat(after.item(ItemCode.WALL_PAINT).labourCost()).isEqualByComparingTo("35.94");
		assertThat(after.item(ItemCode.WALL_PAINT).materialCost())
				.as("paint did not get more expensive because labour did — §6 keeps the two apart")
				.isEqualByComparingTo(before.item(ItemCode.WALL_PAINT).materialCost());
		assertThat(after.item(ItemCode.WALL_PAINT).labourMinutes())
				.as("a price rise does not make the work slower")
				.isEqualByComparingTo(before.item(ItemCode.WALL_PAINT).labourMinutes());
	}

	@Test
	@DisplayName("a material increase leaves the crew rate, and therefore every labour figure, alone")
	void raisesMaterialWithoutTouchingTheCrewRate() {
		PriceBookSummary raised = versions.applyBulkIncrease(
				idOf(ACTIVE), IncreaseTarget.MATERIAL, new BigDecimal("40"));

		PriceBook after = books.findByVersionCode(raised.versionCode()).orElseThrow();

		// The labour column is rewritten by the same statement whichever target ran, so this is the test
		// that the rewrite is a re-derivation and not a rise: 40% on paint must not move a single hour.
		assertThat(after.crewDayCost()).isEqualByComparingTo("5000.00");
		assertThat(after.item(ItemCode.WALL_PAINT).labourCost())
				.isEqualByComparingTo(WALL_PAINT_LABOUR);
		assertThat(after.item(ItemCode.MASKING).labourCost()).isEqualByComparingTo("130.21");
	}

	@Test
	@DisplayName("a bulk increase can raise materials alone, which is the common case after a paint rise")
	void raisesMaterialOnly() {
		PriceBookSummary raised = versions.applyBulkIncrease(
				idOf(ACTIVE), IncreaseTarget.MATERIAL, new BigDecimal("10"));

		PriceBook after = books.findByVersionCode(raised.versionCode()).orElseThrow();
		assertThat(after.item(ItemCode.WALL_PAINT).materialCost()).isEqualByComparingTo("24.20");
		assertThat(after.item(ItemCode.WALL_PAINT).labourCost())
				.isEqualByComparingTo(WALL_PAINT_LABOUR);
	}

	@Test
	@DisplayName("ALL raises both halves by the same percent")
	void raisesBothHalves() {
		PriceBookSummary raised = versions.applyBulkIncrease(
				idOf(ACTIVE), IncreaseTarget.ALL, new BigDecimal("20"));

		PriceBook after = books.findByVersionCode(raised.versionCode()).orElseThrow();
		assertThat(after.crewDayCost()).isEqualByComparingTo("6000.00");
		assertThat(after.item(ItemCode.WALL_PAINT).labourCost()).isEqualByComparingTo("37.50");
		assertThat(after.item(ItemCode.WALL_PAINT).materialCost()).isEqualByComparingTo("26.40");
		assertThat(after.item(ItemCode.MOBILIZATION).labourCost())
				.as("mobilization is 60 minutes of crew time like anything else")
				.isEqualByComparingTo("375.00");
		assertThat(after.item(ItemCode.MOBILIZATION).materialCost())
				.as("the van and the fuel are the material half of it")
				.isEqualByComparingTo("1905.00");
	}

	@Test
	@DisplayName("the increase rounds to the cent, half up, like every other figure")
	void roundsToTheCent() {
		PriceBookSummary raised = versions.applyBulkIncrease(
				idOf(ACTIVE), IncreaseTarget.LABOUR, new BigDecimal("1.5"));

		// The crew day becomes 5,075.00, and masking's 25 minutes are worth 132.161458… of it — a figure
		// the column cannot hold. Rounding at the derivation, half up, like every other figure.
		assertThat(books.findByVersionCode(raised.versionCode()).orElseThrow()
						.item(ItemCode.MASKING).labourCost())
				.isEqualByComparingTo("132.16");
	}

	@Test
	@DisplayName("the source version is not touched, and neither is the quote pointing at it")
	void leavesTheSourceAlone() {
		UUID source = idOf(ACTIVE);
		UUID quote = insertSentQuote(source);

		versions.applyBulkIncrease(source, IncreaseTarget.ALL, new BigDecimal("25"));

		assertThat(books.findByVersionCode(ACTIVE).orElseThrow().item(ItemCode.WALL_PAINT).labourCost())
				.as("the whole point of producing a version instead of editing one")
				.isEqualByComparingTo(WALL_PAINT_LABOUR);
		assertThat(jdbc.queryForObject("SELECT price_book_id FROM quote WHERE id = ?", UUID.class, quote))
				.isEqualTo(source);
		assertThat(books.findActive().orElseThrow().versionCode())
				.as("and the new version is not live until somebody activates it")
				.isEqualTo(ACTIVE);
	}

	@Test
	@DisplayName("the produced version is named after its source, and never collides")
	void namesTheNewVersionAfterItsSource() {
		PriceBookSummary first = versions.applyBulkIncrease(
				idOf(ACTIVE), IncreaseTarget.LABOUR, new BigDecimal("5"));
		PriceBookSummary second = versions.applyBulkIncrease(
				idOf(ACTIVE), IncreaseTarget.LABOUR, new BigDecimal("5"));

		assertThat(first.versionCode()).isEqualTo("REAL-2026-04");
		assertThat(second.versionCode())
				.as("a second increase from the same source must not fail on a name clash")
				.isEqualTo("REAL-2026-05");
	}

	@Test
	@DisplayName("an item can be corrected on a draft version")
	void editsAnItemOnADraftVersion() {
		PriceBookSummary draft = versions.createVersionFrom(idOf(ACTIVE), "TEST-DRAFT-1");

		versions.updateItem(draft.id(), ItemCode.WALL_PAINT,
				new BigDecimal("40.00"), new BigDecimal("7.00"));

		PriceBook edited = books.findByVersionCode("TEST-DRAFT-1").orElseThrow();
		assertThat(edited.item(ItemCode.WALL_PAINT).labourMinutes())
				.as("minutes are editable here — unlike a bulk increase, this is a correction")
				.isEqualByComparingTo("7.00");
		assertThat(edited.item(ItemCode.WALL_PAINT).materialCost()).isEqualByComparingTo("40.00");
		assertThat(edited.item(ItemCode.WALL_PAINT).labourCost())
				.as("the caller never sent a labour cost: 7 minutes at 2,500 a person-day is 36.46")
				.isEqualByComparingTo("36.46");
		assertThat(books.findByVersionCode(ACTIVE).orElseThrow().item(ItemCode.WALL_PAINT).labourCost())
				.isEqualByComparingTo(WALL_PAINT_LABOUR);
	}

	@Test
	@DisplayName("the live version cannot be edited, whatever the panel sends")
	void refusesToEditTheLiveVersion() {
		assertThatThrownBy(() -> versions.updateItem(idOf(ACTIVE), ItemCode.WALL_PAINT,
						new BigDecimal("1.00"), new BigDecimal("1.00")))
				.as("a figure that changed under a quote already sent is a figure nobody can explain")
				.isInstanceOf(PriceBookVersionLocked.class);
	}

	@Test
	@DisplayName("a version a quote points at cannot be edited, even after it is switched off")
	void refusesToEditASupersededVersion() {
		PriceBookSummary next = versions.createVersionFrom(idOf(ACTIVE), "TEST-DRAFT-2");
		UUID superseded = idOf(ACTIVE);
		insertSentQuote(superseded);
		versions.activate(next.id());

		assertThatThrownBy(() -> versions.updateItem(superseded, ItemCode.WALL_PAINT,
						new BigDecimal("1.00"), new BigDecimal("1.00")))
				.as("being switched off is not the same as never having priced anything")
				.isInstanceOf(PriceBookVersionLocked.class);
	}

	@Test
	@DisplayName("a version code cannot be reused")
	void refusesADuplicateVersionCode() {
		assertThatThrownBy(() -> versions.createVersionFrom(idOf(ACTIVE), ACTIVE))
				.isInstanceOf(DuplicateVersionCode.class)
				.hasMessageContaining(ACTIVE);
	}

	@Test
	@DisplayName("cloning a version that does not exist fails before anything is written")
	void refusesToCloneAnUnknownVersion() {
		UUID missing = UUID.randomUUID();

		assertThatThrownBy(() -> versions.createVersionFrom(missing, "TEST-ORPHAN"))
				.isInstanceOf(PriceBookVersionNotFound.class);
		assertThat(jdbc.queryForObject(
						"SELECT count(*) FROM price_book WHERE version_code = 'TEST-ORPHAN'", Integer.class))
				.isZero();
	}

	@Test
	@DisplayName("activating a version that does not exist is an error, not a silent no-op")
	void refusesToActivateAnUnknownVersion() {
		assertThatThrownBy(() -> versions.activate(UUID.randomUUID()))
				.isInstanceOf(PriceBookVersionNotFound.class);
	}

	private UUID idOf(String versionCode) {
		return jdbc.queryForObject(
				"SELECT id FROM price_book WHERE version_code = ?", UUID.class, versionCode);
	}

	private Integer childRows(String table, UUID priceBookId) {
		return jdbc.queryForObject(
				"SELECT count(*) FROM " + table + " WHERE price_book_id = ?", Integer.class, priceBookId);
	}

	/** A quote in the state that matters: sent, priced, pointing at the version that priced it. */
	private UUID insertSentQuote(UUID priceBookId) {
		UUID request = UUID.randomUUID();
		UUID quote = UUID.randomUUID();
		jdbc.update("INSERT INTO quote_request (id, status, price_book_id) VALUES (?, 'QUOTE_SENT', ?)",
				request, priceBookId);
		jdbc.update("INSERT INTO quote (id, quote_request_id, price_book_id, status, total_cost, "
				+ "subtotal, vat_amount, total, band_low, band_high, margin_ratio, estimated_days, "
				+ "total_wall_sqm, total_ceiling_sqm, created_by) "
				+ "VALUES (?, ?, ?, 'SENT', 52509.86, 68262.82, 13652.56, 81915.39, 72085.54, 91745.23, "
				+ "0.3000, 3, 220.83, 92.00, 'OPERATOR')",
				quote, request, priceBookId);
		return quote;
	}
}
