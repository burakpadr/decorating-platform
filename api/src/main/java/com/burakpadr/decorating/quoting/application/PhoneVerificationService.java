package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.customer.domain.event.CustomerIdentified;
import com.burakpadr.decorating.quoting.domain.event.PhoneVerified;
import com.burakpadr.decorating.quoting.domain.model.OtpLocked;
import com.burakpadr.decorating.quoting.domain.model.OtpRefused;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.model.RateBucket;
import com.burakpadr.decorating.quoting.domain.model.TooManyOtpRequests;
import com.burakpadr.decorating.quoting.domain.port.in.SendOtp;
import com.burakpadr.decorating.quoting.domain.port.in.VerifyOtp;
import com.burakpadr.decorating.quoting.domain.port.out.OtpCodes;
import com.burakpadr.decorating.quoting.domain.port.out.PendingPhoneWriter;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RateLimiter;
import com.burakpadr.decorating.quoting.domain.port.out.SmsSender;
import com.burakpadr.decorating.quoting.domain.port.out.VerifiedPhoneWriter;
import com.burakpadr.decorating.quoting.adapter.out.notification.SmsTemplates;
import com.burakpadr.decorating.shared.PhoneNumber;
import com.burakpadr.decorating.shared.Uuid7;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Proving a phone number belongs to the person holding it (workflow §3.1, §11, BOYA-45).
 *
 * <p>§3.1 puts this at the end and explains why: a system that asks for a number on the first screen
 * loses half its visitors, while one that asks after eight minutes of work is asking somebody who has
 * already decided. So this is the last thing between the customer and their quote, which is also what
 * makes it, in §11's words, "the most attackable endpoint".
 *
 * <p>Three defences, and they guard different things. The send limits guard the business's money —
 * every message costs. The attempt counter guards one code against being guessed. And the code is
 * stored as a hash, because the row outlives it: "no code was ever sent" and "that code was used an
 * hour ago" are different answers, so rows are superseded rather than deleted, and a plaintext secret
 * kept past its own life is a secret nobody is watching.
 *
 * <p>The customer row is not created here. §4.1 says one exists only after this moment, and it belongs
 * to another module that this one may not call — so the number goes out as an event and the id comes
 * back as one (decision 0019).
 */
@Service
class PhoneVerificationService implements SendOtp, VerifyOtp {

	/** §11: "lock after 5". */
	private static final int GUESSES_BEFORE_LOCKING = 5;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final QuoteRequestRepository requests;
	private final OtpCodes codes;
	private final RateLimiter limiter;
	private final PendingPhoneWriter pendingPhones;
	private final VerifiedPhoneWriter verifiedPhones;
	private final SmsSender sender;
	private final SmsTemplates templates;
	private final ApplicationEventPublisher events;
	private final Clock clock = Clock.systemUTC();

	private final Duration ttl;
	private final int perPhonePerMinute;
	private final int perPhoneDaily;
	private final int perAddressHourly;

	PhoneVerificationService(QuoteRequestRepository requests, OtpCodes codes, RateLimiter limiter,
			PendingPhoneWriter pendingPhones, VerifiedPhoneWriter verifiedPhones, SmsSender sender,
			SmsTemplates templates, ApplicationEventPublisher events,
			@Value("${decorating.otp.ttl:PT5M}") Duration ttl,
			@Value("${decorating.rate-limit.otp.per-phone-per-minute:1}") int perPhonePerMinute,
			@Value("${decorating.rate-limit.otp.per-phone-daily:5}") int perPhoneDaily,
			@Value("${decorating.rate-limit.otp.per-ip-hourly:10}") int perAddressHourly) {
		this.requests = requests;
		this.codes = codes;
		this.limiter = limiter;
		this.pendingPhones = pendingPhones;
		this.verifiedPhones = verifiedPhones;
		this.sender = sender;
		this.templates = templates;
		this.events = events;
		this.ttl = ttl;
		this.perPhonePerMinute = perPhonePerMinute;
		this.perPhoneDaily = perPhoneDaily;
		this.perAddressHourly = perAddressHourly;
	}

	@Override
	@Transactional
	public Instant send(UUID quoteRequestId, PhoneNumber phone, String scopeKeyForAddress) {
		if (requests.findById(quoteRequestId).isEmpty()) {
			throw new QuoteRequestNotFound(String.valueOf(quoteRequestId));
		}
		requireWithinLimits(phone, scopeKeyForAddress);

		Instant now = clock.instant();
		// Retired before the new one is written: otp_code_live_idx allows exactly one code a customer
		// can still use, and a second SMS must make the first worthless rather than give an attacker
		// who intercepted it a second key.
		codes.supersedeLive(quoteRequestId, now);

		String code = sixDigits();
		Instant expiresAt = now.plus(ttl);
		codes.issue(Uuid7.generate(), quoteRequestId, phone, hash(code), expiresAt);

		// Kept before the message goes, as EstimateSmsService does it: the number is the part that must
		// survive whatever the provider does with the send.
		pendingPhones.storePendingPhone(quoteRequestId, phone);
		sender.send(phone, templates.render("otp-code", "the OTP message", Map.of("code", code)));
		return expiresAt;
	}

	@Override
	@Transactional
	public void verify(UUID quoteRequestId, String code) {
		OtpCodes.LiveCode live = codes.findLive(quoteRequestId)
				.orElseThrow(() -> new OtpRefused("there is no code waiting for this request"));

		if (live.attempts() >= GUESSES_BEFORE_LOCKING) {
			throw new OtpLocked("this code has been guessed at too many times");
		}
		if (!live.expiresAt().isAfter(clock.instant())) {
			throw new OtpRefused("this code has expired");
		}

		if (!MessageDigest.isEqual(live.codeHash().getBytes(StandardCharsets.UTF_8),
				hash(code).getBytes(StandardCharsets.UTF_8))) {
			int attempts = codes.recordFailedAttempt(live.id());
			throw attempts >= GUESSES_BEFORE_LOCKING
					? new OtpLocked("this code has been guessed at too many times")
					: new OtpRefused("that is not the code we sent");
		}

		Instant now = clock.instant();
		codes.consume(live.id(), now);
		verifiedPhones.recordVerified(quoteRequestId, now);
		// The customer row is another module's to write. What comes back is its id (decision 0019).
		events.publishEvent(new PhoneVerified(quoteRequestId, phoneOf(quoteRequestId), now));
	}

	/**
	 * The other half of the round trip: {@code customer} made the row and says which one.
	 *
	 * <p>{@code REQUIRES_NEW} because this runs after that module's transaction committed, and there is
	 * nothing left to join.
	 */
	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void onCustomerIdentified(CustomerIdentified identified) {
		verifiedPhones.attachCustomer(identified.quoteRequestId(), identified.customerId());
	}

	/**
	 * §11's three send limits, phone first.
	 *
	 * <p>The order is the priority. The number is what costs money to reach and what an attacker is
	 * spending; the address is a coarse backstop because Turkish carriers put thousands of subscribers
	 * behind one CGNAT address, and a strict limit there refuses real customers on the same network.
	 */
	private void requireWithinLimits(PhoneNumber phone, String scopeKeyForAddress) {
		String phoneKey = "phone:" + phone.e164();
		if (!limiter.tryAcquire(phoneKey, RateBucket.OTP, Duration.ofMinutes(1), perPhonePerMinute,
				clock.instant())) {
			throw new TooManyOtpRequests("a code was just sent to this number", Duration.ofMinutes(1));
		}
		if (!limiter.tryAcquire(phoneKey, RateBucket.OTP, Duration.ofDays(1), perPhoneDaily,
				clock.instant())) {
			throw new TooManyOtpRequests("too many codes for this number today", Duration.ofHours(24));
		}
		if (!limiter.tryAcquire(scopeKeyForAddress, RateBucket.OTP, Duration.ofHours(1),
				perAddressHourly, clock.instant())) {
			throw new TooManyOtpRequests("too many codes from this connection", Duration.ofHours(1));
		}
	}

	private PhoneNumber phoneOf(UUID quoteRequestId) {
		return pendingPhones.pendingPhone(quoteRequestId)
				.orElseThrow(() -> new IllegalStateException(
						"a code was verified for a request with no number against it"));
	}

	/** Six digits, zero-padded: the message says six and a five-digit code reads as a typo. */
	private static String sixDigits() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	private static String hash(String code) {
		try {
			return Base64.getEncoder().encodeToString(
					MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is required of every JVM", impossible);
		}
	}
}
