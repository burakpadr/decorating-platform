package com.burakpadr.decorating.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * What somebody gets when they clone this repository and start it for the first time (BOYA-72).
 *
 * <p>The project is open source, so the first install is a stranger's install: their crew, their
 * city, their VAT rates. The migrations may therefore ship the schema and nothing that resembles a
 * price. A seeded price book would not merely be unhelpful — an active one lets the engine quote a
 * real customer with figures nobody entered, and the only symptom is that the prices are strange.
 * Setup (BOYA-70) is where those figures come from, and BOYA-69 is what refuses to quote until they
 * do.
 *
 * <p>This test runs its own container rather than the shared one from {@code
 * TestcontainersConfiguration}, and does so deliberately: the shared database is where every other
 * test's fixture lands, so "no price book exists" would pass or fail there according to test
 * ordering. Here the claim is about the migrations alone, which is the claim worth making — a virgin
 * database, migrated exactly the way a new install migrates it.
 */
@Testcontainers
class FreshInstallTest {

	@Container
	static PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

	/** Every table the application needs. A schema-only seed is still a complete schema. */
	private static final List<String> TABLES_THE_APPLICATION_NEEDS = List.of(
			"customer",
			"price_book",
			"price_book_item",
			"price_modifier",
			"room_type_config",
			"service_district",
			"quote_request",
			"room",
			"photo",
			"analysis_job",
			"room_analysis",
			"surface_finding",
			"quote",
			"quote_line_item",
			"quote_adjustment",
			"callback_task",
			"job_outcome",
			"consent",
			"notification",
			"rate_limit_counter",
			"outbox",
			"service_area_waitlist",
			"deletion_request",
			"historical_job");

	@BeforeAll
	static void migrateTheWayANewInstallDoes() {
		Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				// The shipped location, spelled out rather than inherited from application.yml: this test
				// is about what that location contains.
				.locations("classpath:db/migration")
				.load()
				.migrate();
	}

	@Test
	@DisplayName("a fresh install has no price book at all — not even an inactive one")
	void aFreshInstallHasNoPriceBook() {
		assertThat(count("SELECT count(*) FROM price_book"))
				.as("a price book shipped by a migration is a price nobody in this business entered")
				.isZero();
	}

	@Test
	@DisplayName("a fresh install has nothing hanging off a price book either")
	void aFreshInstallHasNoPriceBookContents() {
		// Items, modifiers, room type coefficients and districts all belong to a price book version, so
		// they cannot arrive before one does. Asserted separately because a partial seed — the districts
		// but not the costs, say — is the shape this is most likely to regress into: it looks harmless.
		assertThat(count("SELECT count(*) FROM price_book_item")).as("items").isZero();
		assertThat(count("SELECT count(*) FROM price_modifier")).as("modifiers").isZero();
		assertThat(count("SELECT count(*) FROM room_type_config")).as("room types").isZero();
		assertThat(count("SELECT count(*) FROM service_district")).as("districts").isZero();
	}

	@Test
	@DisplayName("a fresh install still has every table the application needs")
	void aFreshInstallHasTheWholeSchema() {
		List<String> tables = new ArrayList<>();
		query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
				rs -> tables.add(rs.getString(1)));

		assertThat(tables).containsAll(TABLES_THE_APPLICATION_NEEDS);
	}

	private static long count(String sql) {
		long[] result = {-1};
		query(sql, rs -> result[0] = rs.getLong(1));
		return result[0];
	}

	private static void query(String sql, RowReader reader) {
		try (Connection connection = java.sql.DriverManager.getConnection(
						postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			while (rs.next()) {
				reader.read(rs);
			}
		} catch (SQLException e) {
			throw new IllegalStateException(sql, e);
		}
	}

	private interface RowReader {
		void read(ResultSet rs) throws SQLException;
	}
}
