package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.OtpLocked;
import com.burakpadr.decorating.quoting.domain.model.OtpRefused;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.TooManyOtpRequests;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.SendOtp;
import com.burakpadr.decorating.quoting.domain.port.in.VerifyOtp;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.SmsSender;
import com.burakpadr.decorating.shared.PhoneNumber;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifying the customer's phone (workflow §3.1, §11, BOYA-45).
 *
 * <p>§3.1 explains why this is the last thing asked rather than the first: "Baştan numara isteyen
 * sistemler ziyaretçinin yarısını kaybeder. Bu noktada müşteri zaten 8 dakika emek harcamıştır,
 * bırakmaz." The exchange table says the same thing from the other side — the customer gives a phone
 * number and receives access to the quote.
 *
 * <p>Which makes this the one endpoint an attacker gets paid to hit: §11 calls it "the most attackable
 * endpoint" and every rule below is one of its clauses. A second code invalidates the first, a wrong
 * guess is counted, five wrong guesses close the code, and the phone is limited whatever the IP says —
 * "Turkish mobile carriers use CGNAT, thousands of users share an exit IP".
 *
 * <p>The code never appears in the database. What is stored is a hash, because the row outlives the
 * code on purpose: "no code was ever sent" and "that code was used an hour ago" are different answers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PhoneVerificationTest {

	@Autowired
	private SendOtp send;

	@Autowired
	private VerifyOtp verify;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	/**
	 * The transport is mocked so the test can read the code the customer would have read.
	 *
	 * <p>Which is also the only way anybody reads it today: no provider is configured (BOYA-6), so
	 * {@code RecordingSmsSender} writes the body to the application log and returns empty. A test that
	 * fished the code out of the database instead would be testing a column this design does not have.
	 */
	@MockitoBean
	private SmsSender sms;

	private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

	private static final PhoneNumber PHONE = PhoneNumber.of("0532 111 22 33");

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
		jdbc.update("DELETE FROM rate_limit_counter");
		jdbc.update("DELETE FROM customer WHERE phone LIKE '+9053211%'");
	}

	private UUID aRequest() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 3, false, WallCondition.MINOR, null));
		requests.save(draft);
		return draft.id();
	}

	/** The code as the customer reads it off their phone: out of the message that was sent. */
	private String lastCodeSent() {
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		org.mockito.Mockito.verify(sms, org.mockito.Mockito.atLeastOnce())
				.send(org.mockito.ArgumentMatchers.any(), body.capture());
		Matcher digits = SIX_DIGITS.matcher(body.getAllValues().getLast());
		if (!digits.find()) {
			throw new AssertionError("no six-digit code in the message: " + body.getValue());
		}
		return digits.group(1);
	}

	@Test
	@DisplayName("§3.1: a code is sent, and the database never sees it")
	void sendsACodeAndStoresOnlyItsHash() {
		UUID request = aRequest();

		send.send(request, PHONE, "ip:test-1");

		Map<String, Object> row = jdbc.queryForMap(
				"SELECT * FROM otp_code WHERE quote_request_id = ?", request);
		assertThat(row.get("code_hash")).asString().isNotBlank();
		assertThat(row.values()).doesNotContain(lastCodeSent());
		assertThat(row.get("phone")).isEqualTo(PHONE.e164());
	}

	@Test
	@DisplayName("the right code verifies the phone and closes the code behind it")
	void verifiesAndConsumes() {
		UUID request = aRequest();
		send.send(request, PHONE, "ip:test-2");
		String code = lastCodeSent();

		verify.verify(request, code);

		assertThat(jdbc.queryForObject(
				"SELECT phone_verified_at FROM quote_request WHERE id = ?", Instant.class, request))
				.isNotNull();
		assertThat(jdbc.queryForObject(
				"SELECT consumed_at IS NOT NULL FROM otp_code WHERE quote_request_id = ?",
				Boolean.class, request))
				.as("a code that worked once must not work twice")
				.isTrue();
		assertThatThrownBy(() -> verify.verify(request, code)).isInstanceOf(OtpRefused.class);
	}

	@Test
	@DisplayName("§11: asking again replaces the code, and the old one stops working")
	void asecondCodeSupersedesTheFirst() {
		UUID request = aRequest();
		send.send(request, PHONE, "ip:test-3a");
		String first = lastCodeSent();
		jdbc.update("DELETE FROM rate_limit_counter");
		send.send(request, PHONE, "ip:test-3b");
		String second = lastCodeSent();

		assertThat(second).isNotEqualTo(first);
		assertThatThrownBy(() -> verify.verify(request, first))
				.as("an intercepted SMS must not stay usable while the customer presses send again")
				.isInstanceOf(OtpRefused.class);
		verify.verify(request, second);
		assertThat(jdbc.queryForObject(
				"SELECT phone_verified_at FROM quote_request WHERE id = ?", Instant.class, request))
				.isNotNull();
	}

	@Test
	@DisplayName("a wrong guess is counted, and the fifth closes the code (§11)")
	void locksAfterFiveWrongGuesses() {
		UUID request = aRequest();
		send.send(request, PHONE, "ip:test-4");
		String code = lastCodeSent();
		String wrong = code.equals("000000") ? "111111" : "000000";

		for (int guess = 1; guess <= 4; guess++) {
			assertThatThrownBy(() -> verify.verify(request, wrong)).isInstanceOf(OtpRefused.class);
		}
		assertThatThrownBy(() -> verify.verify(request, wrong)).isInstanceOf(OtpLocked.class);

		assertThatThrownBy(() -> verify.verify(request, code))
				.as("the real code is worth nothing once the guessing was stopped: the customer asks for "
						+ "a new one, which is a fresh row and a fresh count")
				.isInstanceOf(OtpLocked.class);
	}

	@Test
	@DisplayName("an expired code is refused")
	void refusesAnExpiredCode() {
		UUID request = aRequest();
		send.send(request, PHONE, "ip:test-5");
		String code = lastCodeSent();
		jdbc.update("UPDATE otp_code SET expires_at = now() - interval '1 minute' "
				+ "WHERE quote_request_id = ?", request);

		assertThatThrownBy(() -> verify.verify(request, code)).isInstanceOf(OtpRefused.class);
	}

	@Test
	@DisplayName("§11: one message a minute to a number, whatever else is going on")
	void limitsSendsPerPhone() {
		UUID first = aRequest();
		UUID second = aRequest();
		send.send(first, PHONE, "ip:test-6a");

		assertThatThrownBy(() -> send.send(second, PHONE, "ip:test-6b"))
				.as("the limit is on the number, because the number is what costs money to reach")
				.isInstanceOf(TooManyOtpRequests.class);
	}

	@Test
	@DisplayName("verifying a request that was never sent a code is refused, not crashed")
	void refusesWhenNoCodeWasSent() {
		assertThatThrownBy(() -> verify.verify(aRequest(), "123456")).isInstanceOf(OtpRefused.class);
	}

	@Test
	@DisplayName("the code is six digits, because that is what the SMS says it is")
	void theCodeIsSixDigits() {
		UUID request = aRequest();
		send.send(request, PHONE, "ip:test-7");

		assertThat(lastCodeSent()).matches("\\d{6}");
	}

	@Test
	@DisplayName("acceptance: verification creates the customer and hands the request its id")
	void createsTheCustomerOnVerification() {
		UUID request = aRequest();
		send.send(request, PHONE, "ip:test-8");

		verify.verify(request, lastCodeSent());

		List<Map<String, Object>> customers = jdbc.queryForList(
				"SELECT id, phone FROM customer WHERE phone = ?", PHONE.e164());
		assertThat(customers)
				.as("§4.1: a customer row appears only on successful verification, never before")
				.hasSize(1);
		assertThat(jdbc.queryForObject(
				"SELECT customer_id FROM quote_request WHERE id = ?", UUID.class, request))
				.isEqualTo(customers.get(0).get("id"));
		assertThat(jdbc.queryForObject(
				"SELECT pending_phone FROM quote_request WHERE id = ?", String.class, request))
				.as("the schema says the number moves to customer and is nulled here")
				.isNull();
	}

	@Test
	@DisplayName("a returning customer resolves to the row they already have")
	void reusesAnExistingCustomer() {
		UUID first = aRequest();
		send.send(first, PHONE, "ip:test-9a");
		verify.verify(first, lastCodeSent());
		UUID customerId = jdbc.queryForObject(
				"SELECT customer_id FROM quote_request WHERE id = ?", UUID.class, first);

		jdbc.update("DELETE FROM rate_limit_counter");
		UUID second = aRequest();
		send.send(second, PHONE, "ip:test-9b");
		verify.verify(second, lastCodeSent());

		assertThat(jdbc.queryForObject(
				"SELECT customer_id FROM quote_request WHERE id = ?", UUID.class, second))
				.as("lookup is by phone, which is how repeat business becomes visible at all")
				.isEqualTo(customerId);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM customer WHERE phone = ?", Integer.class, PHONE.e164()))
				.isEqualTo(1);
	}
}
