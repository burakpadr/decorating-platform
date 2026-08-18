package com.burakpadr.decorating.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The import that puts completed jobs into the calibration record — {@code calibration/merge.sql},
 * driven by {@code import.sql}.
 *
 * <p>Under forward capture (ADR 0012) this import is not a one-time handover: it runs again every time
 * a batch of finished jobs is recorded, and the file will routinely contain jobs already imported.
 * That makes its behaviour on a repeat run the whole question. Two ways to get it wrong, both silent:
 * overwrite a recorded job with a re-typed row, or drop a work row whose job reference matches
 * nothing.
 *
 * <p>The two {@code \copy} lines of {@code import.sql} are psql meta-commands and cannot run over
 * JDBC, which is why the DDL and the merge are separate files — everything that decides what enters
 * the dataset is under the build; only reading the two CSV files is not.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HistoricalJobImportTest {

	private static final String STAGING_SQL = read("calibration/staging.sql");
	private static final String MERGE_SQL = read("calibration/merge.sql");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@BeforeEach
	void createStagingTables() {
		dropStagingTables();
		jdbc.execute(STAGING_SQL);
	}

	@AfterEach
	void dropStagingTables() {
		jdbc.execute("DROP TABLE IF EXISTS staging_job, staging_item, import_skipped");
	}

	@Test
	@DisplayName("a repeat import lands the new jobs, reports the recorded ones and overwrites nothing")
	void repeatImportLandsNewJobsAndReportsTheRest() {
		recordJob("2026-0201", "52000.00");
		stageJob("2026-0201", "60000.00");
		stageJob("2026-0202", "31000.00");

		merge();

		assertThat(cost("2026-0201"))
				.as("a re-typed row must not silently rewrite a job already in the dataset")
				.isEqualByComparingTo("52000.00");
		assertThat(cost("2026-0202")).isEqualByComparingTo("31000.00");
		assertThat(jdbc.queryForList("SELECT job_ref FROM import_skipped", String.class))
				.as("skipping has to be reported, or the operator believes the correction landed")
				.containsExactly("2026-0201");
	}

	@Test
	@DisplayName("a work row for a job nobody recorded rejects the whole import")
	void aWorkRowForAnUnknownJobRejectsTheImport() {
		stageJob("2026-0203", "28000.00");
		jdbc.update(
				"INSERT INTO staging_item (job_ref, code, quantity) VALUES ('2026-9999', 'WALL_PAINT', 10)");

		assertThatThrownBy(this::merge).hasMessageContaining("historical_job_id");

		assertThat(jdbc.queryForObject(
						"SELECT count(*) FROM historical_job WHERE job_ref = ?", Integer.class, "2026-0203"))
				.as("a rejected batch must leave nothing behind for the next run to double-count")
				.isEqualTo(0);
	}

	@Test
	@DisplayName("work items missed on an earlier import still land against the recorded job")
	void itemsMissedEarlierStillLand() {
		UUID jobId = recordJob("2026-0204", "44000.00");
		jdbc.update(
				"INSERT INTO historical_job_item (id, historical_job_id, code, quantity) "
						+ "VALUES (?, ?, 'WALL_PAINT', 148.00)",
				UUID.randomUUID(), jobId);
		stageJob("2026-0204", "44000.00");
		jdbc.update(
				"INSERT INTO staging_item (job_ref, code, quantity) VALUES ('2026-0204', 'WALL_PAINT', 148)");
		jdbc.update(
				"INSERT INTO staging_item (job_ref, code, quantity) VALUES ('2026-0204', 'CEILING_PAINT', 62)");

		merge();

		List<String> codes = jdbc.queryForList(
				"SELECT i.code FROM historical_job_item i JOIN historical_job h ON h.id = i.historical_job_id "
						+ "WHERE h.job_ref = ?",
				String.class, "2026-0204");

		assertThat(codes)
				.as("the job is skipped but a code it was missing is not")
				.containsExactlyInAnyOrder("WALL_PAINT", "CEILING_PAINT");
	}

	/** psql runs the merge in one transaction; mirror that, so a rejection rolls the batch back. */
	private void merge() {
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> jdbc.execute(MERGE_SQL));
	}

	private UUID recordJob(String jobRef, String actualCost) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"INSERT INTO historical_job (id, job_ref, completed_on, net_area_m2, quoted_total_ex_vat, "
						+ "actual_cost) VALUES (?, ?, DATE '2026-06-01', 90.00, 60000.00, ?::numeric)",
				id, jobRef, actualCost);
		return id;
	}

	private void stageJob(String jobRef, String actualCost) {
		jdbc.update(
				"INSERT INTO staging_job (job_ref, completed_on, net_area_m2, quoted_total_ex_vat, "
						+ "actual_cost) VALUES (?, DATE '2026-06-01', 90.00, 60000.00, ?::numeric)",
				jobRef, actualCost);
	}

	private BigDecimal cost(String jobRef) {
		return jdbc.queryForObject(
				"SELECT actual_cost FROM historical_job WHERE job_ref = ?", BigDecimal.class, jobRef);
	}

	private static String read(String classpathLocation) {
		try {
			return new ClassPathResource(classpathLocation)
					.getContentAsString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("missing " + classpathLocation, e);
		}
	}
}
