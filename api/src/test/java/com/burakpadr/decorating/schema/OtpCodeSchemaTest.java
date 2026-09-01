package com.burakpadr.decorating.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The OTP code table (§7's {@code /api/otp/*}, workflow §3.1, BOYA-45).
 *
 * <p>Asserted at the schema level because the constraint is the feature. §11 makes this "the most
 * attackable endpoint" in the system, and the rule that matters most cannot be written in Java alone:
 * a request may have only one code a customer can still use. If asking for a second code left the
 * first one live, an attacker who intercepted an earlier SMS would keep a working key for as long as
 * the customer kept pressing "send again".
 *
 * <p>The rows are superseded, never deleted. "No code was ever sent to this request" and "the code was
 * used an hour ago" are different answers to a customer on the phone, and a table that deletes cannot
 * tell them apart.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OtpCodeSchemaTest {

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private QuoteRequestRepository requests;

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	private UUID aRequest() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 3, false, WallCondition.MINOR, null));
		requests.save(draft);
		return draft.id();
	}

	private UUID insert(UUID requestId, String hash) {
		UUID id = Uuid7.generate();
		jdbc.update("""
				INSERT INTO otp_code (id, quote_request_id, phone, code_hash, expires_at)
				VALUES (?, ?, ?, ?, ?)
				""", id, requestId, "+905321234567", hash,
				java.sql.Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES)));
		return id;
	}

	@Test
	@DisplayName("a request holds one code a customer can still use, and no more")
	void onlyOneLiveCodePerRequest() {
		UUID request = aRequest();
		insert(request, "first");

		assertThatThrownBy(() -> insert(request, "second"))
				.as("§11: a second code must replace the first, not stand beside it — an intercepted SMS "
						+ "would otherwise stay usable for as long as the customer kept asking")
				.hasMessageContaining("otp_code_live_idx");
	}

	@Test
	@DisplayName("superseding the old code makes room for the new one")
	void supersedingFreesTheSlot() {
		UUID request = aRequest();
		UUID first = insert(request, "first");

		jdbc.update("UPDATE otp_code SET superseded_at = now() WHERE id = ?", first);
		UUID second = insert(request, "second");

		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM otp_code WHERE quote_request_id = ?", Integer.class, request))
				.as("the old row stays: it is the difference between 'never sent' and 'replaced'")
				.isEqualTo(2);
		assertThat(second).isNotEqualTo(first);
	}

	@Test
	@DisplayName("a consumed code frees the slot too, and stays on the record")
	void consumingFreesTheSlot() {
		UUID request = aRequest();
		UUID first = insert(request, "first");

		jdbc.update("UPDATE otp_code SET consumed_at = now() WHERE id = ?", first);
		insert(request, "second");

		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM otp_code WHERE quote_request_id = ? AND consumed_at IS NOT NULL",
				Integer.class, request)).isEqualTo(1);
	}

	@Test
	@DisplayName("two requests may each hold their own live code")
	void theIndexIsPerRequest() {
		insert(aRequest(), "one");
		insert(aRequest(), "two");

		assertThat(jdbc.queryForObject("SELECT count(*) FROM otp_code", Integer.class))
				.isGreaterThanOrEqualTo(2);
	}

	@Test
	@DisplayName("attempts start at zero and cannot go below it")
	void attemptsAreCounted() {
		UUID request = aRequest();
		UUID code = insert(request, "hash");

		assertThat(jdbc.queryForObject(
				"SELECT attempts FROM otp_code WHERE id = ?", Integer.class, code)).isZero();
		assertThatThrownBy(() -> jdbc.update("UPDATE otp_code SET attempts = -1 WHERE id = ?", code))
				.hasMessageContaining("otp_attempts_not_negative");
	}

	@Test
	@DisplayName("the codes go when the request goes: §12 keeps no secret past its subject")
	void cascadesWithTheRequest() {
		UUID request = aRequest();
		insert(request, "hash");

		jdbc.update("DELETE FROM quote_request WHERE id = ?", request);

		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM otp_code WHERE quote_request_id = ?", Integer.class, request))
				.isZero();
	}

	@Test
	@DisplayName("the code itself is not a column — only a hash of it")
	void storesNoPlaintextCode() {
		assertThat(jdbc.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_name = 'otp_code'
				""", String.class))
				.as("a six-digit secret whose row outlives it is a secret nobody is watching")
				.contains("code_hash")
				.doesNotContain("code");
	}
}
