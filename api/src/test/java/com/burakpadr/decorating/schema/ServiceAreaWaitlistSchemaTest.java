package com.burakpadr.decorating.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The out-of-service-area waitlist (`is-akis-sureci.md` §7 decision 1 and §8): a visitor eliminated
 * by the first question can ask to be told when their district opens.
 *
 * <p>Asserted at the schema level rather than through a repository because the constraints *are* the
 * feature. A duplicate signup must not create a second row — the district opening later would
 * otherwise text the same person twice, which is the one thing that turns a goodwill list into a
 * complaint.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ServiceAreaWaitlistSchemaTest {

	@Autowired
	private JdbcTemplate jdbc;

	private void insert(String district, String phone) {
		jdbc.update(
				"INSERT INTO service_area_waitlist (id, district_code, phone, consent_text_version) "
						+ "VALUES (?, ?, ?, ?)",
				UUID.randomUUID(), district, phone, "waitlist-v1");
	}

	@Test
	@DisplayName("a visitor can join the waitlist for a district that is not served")
	void acceptsASignup() {
		insert("CORLU", "+905550000001");

		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM service_area_waitlist WHERE district_code = ?",
				Integer.class, "CORLU");

		assertThat(count).isEqualTo(1);
	}

	@Test
	@DisplayName("the same number cannot join the same district twice")
	void rejectsADuplicateSignup() {
		insert("GEBZE", "+905550000002");

		assertThatThrownBy(() -> insert("GEBZE", "+905550000002"))
				.hasMessageContaining("service_area_waitlist");
	}

	@Test
	@DisplayName("the same number may wait for two different districts")
	void allowsTwoDistrictsForOneNumber() {
		insert("IZMIT", "+905550000003");
		insert("DARICA", "+905550000003");

		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM service_area_waitlist WHERE phone = ?",
				Integer.class, "+905550000003");

		assertThat(count).isEqualTo(2);
	}

	@Test
	@DisplayName("notified_at starts null so an unnotified signup is findable")
	void startsUnnotified() {
		insert("SILIVRI_KOY", "+905550000004");

		Integer pending = jdbc.queryForObject(
				"SELECT count(*) FROM service_area_waitlist WHERE notified_at IS NULL AND phone = ?",
				Integer.class, "+905550000004");

		assertThat(pending).isEqualTo(1);
	}

	@Test
	@DisplayName("rate limiting recognises the WAITLIST bucket")
	void rateLimitBucketAcceptsWaitlist() {
		// Collecting a phone number is an abuse vector even without an immediate SMS, so the bucket
		// has to exist before the endpoint does.
		jdbc.update(
				"INSERT INTO rate_limit_counter (id, scope_key, bucket, window_start) "
						+ "VALUES (?, ?, 'WAITLIST', now())",
				UUID.randomUUID(), "ip:203.0.113.9");

		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM rate_limit_counter WHERE bucket = 'WAITLIST'", Integer.class);

		assertThat(count).isEqualTo(1);
	}
}
