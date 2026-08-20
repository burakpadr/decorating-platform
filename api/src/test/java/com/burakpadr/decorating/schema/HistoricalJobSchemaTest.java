package com.burakpadr.decorating.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The record of completed jobs (BOYA-2, workflow §12): the second half of Phase 0, and the only
 * evidence that will say whether the price book's figures are this business's figures. No such history
 * exists, so it is built forward, one row per job as it finishes — ADR 0012.
 *
 * <p>It cannot go in {@code job_outcome}. That table's {@code quote_request_id} is {@code NOT NULL}
 * and a foreign key — by design, since it records stage 8 against a quote the system produced. Jobs
 * that predate the system have no quote request and never will, so a table whose rows must point at
 * one has no room for them.
 *
 * <p>Asserted at the schema level because the constraints are the feature. Calibration data is
 * arithmetic on rows nobody reads individually: a job imported twice, a cost split that does not add
 * up, or a work code the price book does not know produces a plausible number that is wrong, with no
 * visible symptom — the same failure mode §5.11's figures have, one layer up.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HistoricalJobSchemaTest {

	/** Intake template for the job rows; its header is the import contract. */
	private static final Path JOB_TEMPLATE =
			Path.of("src/main/resources/calibration/historical-jobs-template.csv");

	/** Intake template for "yapılan işler" — one row per work item per job. */
	private static final Path ITEM_TEMPLATE =
			Path.of("src/main/resources/calibration/historical-job-items-template.csv");

	/** The Turkish sheet the business fills the templates from. */
	private static final Path INTAKE_GUIDE = Path.of("../docs/product/tamamlanan-is-kaydi.md");

	/** Columns the system owns; the business never types them, so they are not in the template. */
	private static final List<String> SYSTEM_OWNED_JOB_COLUMNS = List.of("id", "recorded_at");

	@Autowired
	private JdbcTemplate jdbc;

	private UUID insertJob(String jobRef, String grossArea, String netArea) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"INSERT INTO historical_job (id, job_ref, completed_on, district_code, layout, scope, "
						+ "furnishing, wall_condition, gross_area_m2, net_area_m2, door_count, "
						+ "quoted_total_ex_vat, actual_total_ex_vat, actual_cost, actual_labour_cost, "
						+ "actual_material_cost, actual_days, crew_size, notes) "
						+ "VALUES (?, ?, DATE '2026-03-14', 'KADIKOY', 'THREE_PLUS_ONE', 'WHOLE_HOME', "
						+ "'FURNISHED', 'MINOR', ?::numeric, ?::numeric, 8, "
						+ "68000.00, NULL, 52000.00, 33000.00, 19000.00, 3, 3, NULL)",
				id, jobRef, grossArea, netArea);
		return id;
	}

	@Test
	@DisplayName("a job that predates the system is recorded without a quote request")
	void acceptsAJobThatPredatesTheSystem() {
		insertJob("2026-0001", "112.00", "92.00");

		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM historical_job WHERE job_ref = ?", Integer.class, "2026-0001");

		assertThat(count).isEqualTo(1);
	}

	@Test
	@DisplayName("the same job cannot be imported twice")
	void rejectsASecondImportOfTheSameJob() {
		insertJob("2026-0002", "112.00", "92.00");

		assertThatThrownBy(() -> insertJob("2026-0002", "112.00", "92.00"))
				.as("re-running an import must not double-count a job into the calibration")
				.hasMessageContaining("historical_job_job_ref_key");
	}

	@Test
	@DisplayName("a job with no area recorded is rejected: every calibration figure is per m²")
	void rejectsAJobWithNoAreaRecorded() {
		assertThatThrownBy(() -> insertJob("2026-0003", null, null))
				.hasMessageContaining("historical_job_area_recorded_check");
	}

	@Test
	@DisplayName("a labour/material split that does not add up to the total is rejected")
	void rejectsACostSplitThatDoesNotAddUp() {
		assertThatThrownBy(() -> jdbc.update(
						"INSERT INTO historical_job (id, job_ref, completed_on, net_area_m2, "
								+ "quoted_total_ex_vat, actual_cost, actual_labour_cost, actual_material_cost) "
								+ "VALUES (?, '2026-0004', DATE '2026-03-14', 92.00, 68000.00, 52000.00, "
								+ "33000.00, 12000.00)",
						UUID.randomUUID()))
				.hasMessageContaining("historical_job_cost_split_check");
	}

	@Test
	@DisplayName("the calibration row states the margin the job actually realised, against the active book")
	void calibrationRowStatesTheRealisedMarginAgainstTheActiveBook() {
		insertJob("2026-0005", "112.00", "92.00");

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT * FROM historical_job_calibration WHERE job_ref = ?", "2026-0005");

		// 68,000 quoted ex-VAT on 52,000 of cost is a realised margin of 30.77%.
		assertThat((BigDecimal) row.get("realised_margin_ratio")).isEqualByComparingTo("0.3077");
		assertThat((BigDecimal) row.get("price_book_margin_ratio")).isEqualByComparingTo("0.3000");
		assertThat((BigDecimal) row.get("margin_gap")).isEqualByComparingTo("0.0077");
		assertThat((BigDecimal) row.get("net_area_m2_used")).isEqualByComparingTo("92.00");
		assertThat((BigDecimal) row.get("quoted_per_m2")).isEqualByComparingTo("739.13");
		assertThat((BigDecimal) row.get("actual_cost_per_m2")).isEqualByComparingTo("565.22");
		// 33,000 of labour over 3 days says the crew day cost is 11,000, against the 7,500 the live book
		// carries. This column is the mechanism ADR 0012 committed to: a recorded job is allowed to
		// contradict the price book, and this is the number that does the contradicting.
		assertThat((BigDecimal) row.get("implied_crew_day_cost")).isEqualByComparingTo("11000.00");
		assertThat((BigDecimal) row.get("implied_gross_to_net_ratio")).isEqualByComparingTo("0.8214");
		assertThat(row.get("price_book_version"))
				.as("the view names whichever version is live, so this follows the migrations rather than "
						+ "pinning a version code a later calibration pass would have to come back and edit")
				.isEqualTo(jdbc.queryForObject(
						"SELECT version_code FROM price_book WHERE active = true", String.class));
		assertThat(row.get("net_area_estimated")).isEqualTo(false);
		assertThat(row.get("quote_accuracy_ratio"))
				.as("no invoice recorded means unknown, not 'invoiced exactly what was quoted'")
				.isNull();
	}

	@Test
	@DisplayName("a job recorded in gross m² is carried into the calibration, and flagged as estimated")
	void calibrationDerivesNetAreaFromGrossAndSaysSo() {
		insertJob("2026-0006", "112.00", null);

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT net_area_m2_used, net_area_estimated, implied_gross_to_net_ratio "
						+ "FROM historical_job_calibration WHERE job_ref = ?",
				"2026-0006");

		// 112 × the active book's 0.82, so the figure is only as good as a coefficient still to be
		// calibrated — which is exactly why the flag travels with it.
		assertThat((BigDecimal) row.get("net_area_m2_used")).isEqualByComparingTo("91.84");
		assertThat(row.get("net_area_estimated")).isEqualTo(true);
		assertThat(row.get("implied_gross_to_net_ratio")).isNull();
	}

	@Test
	@DisplayName("where the invoice differs from the quote, the margin is measured against the invoice")
	void realisedMarginFollowsTheInvoiceNotTheQuote() {
		jdbc.update(
				"INSERT INTO historical_job (id, job_ref, completed_on, net_area_m2, "
						+ "quoted_total_ex_vat, actual_total_ex_vat, actual_cost) "
						+ "VALUES (?, '2026-0009', DATE '2026-04-02', 92.00, 68000.00, 74000.00, 52000.00)",
				UUID.randomUUID());

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT quote_accuracy_ratio, realised_margin_ratio, invoiced_total_ex_vat "
						+ "FROM historical_job_calibration WHERE job_ref = ?",
				"2026-0009");

		// A job that ran over and was invoiced at 74,000 earned 42.31%, not the 30.77% the quote implies.
		// Measuring the price list against the quote would credit it with an accuracy it did not have.
		assertThat((BigDecimal) row.get("quote_accuracy_ratio")).isEqualByComparingTo("1.0882");
		assertThat((BigDecimal) row.get("realised_margin_ratio")).isEqualByComparingTo("0.4231");
		assertThat((BigDecimal) row.get("invoiced_total_ex_vat")).isEqualByComparingTo("74000.00");
	}

	@Test
	@DisplayName("a work code the active price book does not know is reported, not silently priced")
	void unknownWorkCodesAreReported() {
		UUID jobId = insertJob("2026-0007", "112.00", "92.00");
		insertItem(jobId, "WALL_PAINT", "221.00");
		insertItem(jobId, "FLOOR_SANDING", "48.00");

		List<String> unknown = jdbc.queryForList(
				"SELECT code FROM historical_job_unknown_item_code WHERE job_ref = ?",
				String.class, "2026-0007");

		assertThat(unknown)
				.as("a code with no price book row compares the job against nothing")
				.containsExactly("FLOOR_SANDING");
	}

	@Test
	@DisplayName("a job cannot carry the same work code twice")
	void rejectsADuplicateWorkCodeOnOneJob() {
		UUID jobId = insertJob("2026-0008", "112.00", "92.00");
		insertItem(jobId, "WALL_PAINT", "221.00");

		assertThatThrownBy(() -> insertItem(jobId, "WALL_PAINT", "12.00"))
				.as("two rows for one code silently double the quantity the comparison sees")
				.hasMessageContaining("historical_job_item_job_code_key");
	}

	@Test
	@DisplayName("the record holds no personal data: it outlives every retention window in §12")
	void theRecordCarriesNoPersonalData() {
		List<String> columns = jdbc.queryForList(
				"SELECT column_name FROM information_schema.columns "
						+ "WHERE table_name IN ('historical_job', 'historical_job_item')",
				String.class);

		assertThat(columns)
				.as("calibration data is kept indefinitely, so it must not be personal data")
				.noneMatch(c -> c.contains("phone")
						|| c.contains("name")
						|| c.contains("email")
						|| c.contains("address"));
	}

	@Test
	@DisplayName("the job template's header is exactly the columns it loads into, in order")
	void jobTemplateHeaderMatchesTheTableItLoadsInto() throws IOException {
		List<String> expected = jdbc.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'historical_job' "
						+ "ORDER BY ordinal_position",
				String.class);
		expected.removeAll(SYSTEM_OWNED_JOB_COLUMNS);

		assertThat(header(JOB_TEMPLATE)).isEqualTo(expected);
	}

	@Test
	@DisplayName("the work item template's header is job_ref plus the columns it loads into")
	void itemTemplateHeaderMatchesTheTableItLoadsInto() throws IOException {
		List<String> expected = jdbc.queryForList(
				"SELECT column_name FROM information_schema.columns "
						+ "WHERE table_name = 'historical_job_item' ORDER BY ordinal_position",
				String.class);
		// The template addresses a job by the business's own reference; the foreign key is resolved
		// on import, so id and historical_job_id are never typed.
		expected.removeAll(List.of("id", "historical_job_id"));
		expected.add(0, "job_ref");

		assertThat(header(ITEM_TEMPLATE)).isEqualTo(expected);
	}

	@Test
	@DisplayName("the Turkish intake sheet documents every column of both templates")
	void intakeGuideDocumentsEveryTemplateColumn() throws IOException {
		String guide = Files.readString(INTAKE_GUIDE, StandardCharsets.UTF_8);
		List<String> documented = codeSpans(guide);

		assertThat(documented)
				.as("a column the business is never told about arrives empty or wrong")
				.containsAll(header(JOB_TEMPLATE))
				.containsAll(header(ITEM_TEMPLATE));
	}

	private void insertItem(UUID jobId, String code, String quantity) {
		jdbc.update(
				"INSERT INTO historical_job_item (id, historical_job_id, code, quantity) "
						+ "VALUES (?, ?, ?, ?::numeric)",
				UUID.randomUUID(), jobId, code, quantity);
	}

	private static List<String> header(Path template) throws IOException {
		String first = Files.readAllLines(template, StandardCharsets.UTF_8).getFirst();
		return List.of(first.split(","));
	}

	/** Every `backticked` token in the guide — the column names are written that way. */
	private static List<String> codeSpans(String markdown) {
		Matcher matcher = Pattern.compile("`([^`]+)`").matcher(markdown);
		List<String> spans = new java.util.ArrayList<>();
		while (matcher.find()) {
			spans.add(matcher.group(1));
		}
		return spans;
	}
}
