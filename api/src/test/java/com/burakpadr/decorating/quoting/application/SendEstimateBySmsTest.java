package com.burakpadr.decorating.quoting.application;

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
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.in.SendEstimateBySms;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.ResumeTokens;
import com.burakpadr.decorating.shared.PhoneNumber;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Sending the range by SMS (§1.5, §13, BOYA-33).
 *
 * <p>§1.5 calls the result screen the biggest loss point in the process and this the option that
 * matters most: the customer who sees a range and leaves has given no number and cannot be reached
 * again. So the valuable half is not the message — it is that the number is kept and the row survives.
 *
 * <p>No provider is wired (BOYA-6 is still an open business decision), so the send does not happen and
 * the row stays QUEUED. That is the acceptance criterion, not a shortcut: "gönderilmeyen SMS sessizce
 * kaybolmaz — outbox'ta kalır".
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SendEstimateBySmsTest {

	@Autowired
	private SendEstimateBySms sms;

	@Autowired
	private EstimateStageOne estimates;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private ResumeTokens resumeTokens;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM notification WHERE recipient LIKE '+905551%'");
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	private UUID priced() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, WallCondition.MINOR, null));
		requests.save(draft);
		estimates.estimate(draft.id());
		return draft.id();
	}

	@Test
	@DisplayName("the number is kept and the message is queued, whether or not anything sends it")
	void keepsTheNumberAndQueuesTheMessage() {
		UUID id = priced();

		sms.send(id, PhoneNumber.of("0555 123 45 67"));

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT * FROM notification WHERE quote_request_id = ?", id);
		assertThat(row.get("channel")).isEqualTo("SMS");
		assertThat(row.get("template_code")).isEqualTo("ESTIMATE_SMS");
		assertThat(row.get("recipient")).isEqualTo("+905551234567");
		assertThat(row.get("status"))
				.as("no provider is wired, so it is queued rather than claimed as sent — §1.5's option is "
						+ "worth having the moment the number is kept")
				.isEqualTo("QUEUED");
		assertThat(row.get("sent_at")).isNull();
		assertThat(row.get("provider_ref")).isNull();

		assertThat(jdbc.queryForObject(
						"SELECT pending_phone FROM quote_request WHERE id = ?", String.class, id))
				.as("§4.2's pre-verification field: this is the number a customer who never verifies is "
						+ "reached on, and the only reason this option exists")
				.isEqualTo("+905551234567");
	}

	@Test
	@DisplayName("the message carries a link that works on another device")
	void issuesAResumeTokenForTheLink() {
		UUID id = priced();

		sms.send(id, PhoneNumber.of("05551234567"));

		String token = jdbc.queryForObject(
				"SELECT resume_token FROM quote_request WHERE id = ?", String.class, id);
		assertThat(token)
				.as("§7: QR and SMS links both hit /resume/{token}. A link the phone cannot open is a "
						+ "message that cost money and did nothing — the session cookie is on the laptop")
				.isNotBlank();
		assertThat(resumeTokens.resolve(token)).contains(id);
	}

	@Test
	@DisplayName("asking twice does not mint a second link, so the first SMS keeps working")
	void reusesTheSameToken() {
		UUID id = priced();

		sms.send(id, PhoneNumber.of("05551234567"));
		String first = jdbc.queryForObject(
				"SELECT resume_token FROM quote_request WHERE id = ?", String.class, id);
		sms.send(id, PhoneNumber.of("05551234568"));

		assertThat(jdbc.queryForObject(
						"SELECT resume_token FROM quote_request WHERE id = ?", String.class, id))
				.isEqualTo(first);
	}

	@Test
	@DisplayName("a draft with no range yet has nothing to send")
	void refusesADraftWithNoEstimate() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate());
		requests.save(draft);

		assertThatThrownBy(() -> sms.send(draft.id(), PhoneNumber.of("05551234567")))
				.as("the message is the range; without one there is nothing to put in it")
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("an unknown token resolves to nothing rather than to somebody else's draft")
	void refusesAnUnknownToken() {
		assertThat(resumeTokens.resolve("not-a-token")).isEmpty();
		assertThat(resumeTokens.resolve(null)).isEmpty();
	}

	@Test
	@DisplayName("an expired token stops working")
	void expiresTheToken() {
		UUID id = priced();
		sms.send(id, PhoneNumber.of("05551234567"));
		String token = jdbc.queryForObject(
				"SELECT resume_token FROM quote_request WHERE id = ?", String.class, id);

		jdbc.update("UPDATE quote_request SET resume_token_expires = now() - interval '1 minute' "
				+ "WHERE id = ?", id);

		assertThat(resumeTokens.resolve(token))
				.as("a link in an SMS outlives the conversation it belonged to unless something stops it")
				.isEmpty();
	}
}
