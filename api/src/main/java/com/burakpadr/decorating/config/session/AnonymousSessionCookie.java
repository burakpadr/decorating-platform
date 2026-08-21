package com.burakpadr.decorating.config.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.ResponseCookie;

/**
 * The anonymous session: a signed cookie bound to one {@code quote_request.id} (§7).
 *
 * <p>Stage 1 creates no customer row — §4.1 is explicit that one appears only on OTP verification — so
 * until a phone is verified this cookie is the entire access control over a stranger's answers, their
 * phone number and their photographs. It is not a convenience.
 *
 * <p>The token is {@code 1.<id>.<expiry>.<signature>}: a version, so a format change does not have to
 * guess what it is reading; the id and the expiry inside what is signed rather than beside it, so
 * neither can be edited; and HMAC-SHA256 over the pair. Deterministic, so a refresh does not mint a new
 * cookie for the same request and the browser is not asked to store two.
 *
 * <p>Not encryption. A quote request id is not a secret — it is in the URL — and hiding it would buy
 * nothing. What the signature buys is that a browser cannot name a request it was never given.
 */
public final class AnonymousSessionCookie {

	/** Short, because it travels on every request, and prefixed so it is recognisable in a jar of them. */
	public static final String NAME = "dp_session";

	private static final String VERSION = "1";
	private static final String ALGORITHM = "HmacSHA256";

	/**
	 * Below this a secret is guessable offline, and a guessable key is the same as no signature at all.
	 * 32 characters is not a cryptographic argument, it is a floor under carelessness.
	 */
	private static final int MINIMUM_SECRET_LENGTH = 32;

	private final byte[] key;
	private final Duration ttl;
	private final String domain;
	private final Clock clock;

	public AnonymousSessionCookie(String secret, Duration ttl, String domain, Clock clock) {
		if (secret == null || secret.isBlank()) {
			throw new IllegalArgumentException(
					"decorating.session.secret is required: without it the session cookie is unsigned, "
							+ "and an unsigned cookie lets any browser name any quote request");
		}
		if (secret.strip().length() < MINIMUM_SECRET_LENGTH) {
			throw new IllegalArgumentException(
					"decorating.session.secret must be at least " + MINIMUM_SECRET_LENGTH + " characters");
		}
		this.key = secret.getBytes(StandardCharsets.UTF_8);
		this.ttl = ttl;
		this.domain = domain == null ? "" : domain.strip();
		this.clock = clock;
	}

	/** The token for a request, valid from now for the configured window. */
	public String mint(UUID quoteRequestId) {
		long expiresAt = clock.instant().plus(ttl).getEpochSecond();
		String payload = VERSION + "." + quoteRequestId + "." + expiresAt;
		return payload + "." + sign(payload);
	}

	/**
	 * The request this token is for, or empty for every other case — wrong signature, edited id, stretched
	 * expiry, expired, malformed, absent.
	 *
	 * <p>One return type for all of them on purpose: a caller that could tell "expired" from "forged"
	 * would be tempted to treat one of them gently, and the answer to both is the same.
	 */
	public Optional<UUID> verify(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		String[] parts = token.split("\\.");
		if (parts.length != 4 || !VERSION.equals(parts[0])) {
			return Optional.empty();
		}
		String payload = parts[0] + "." + parts[1] + "." + parts[2];
		if (!MessageDigest.isEqual(
				sign(payload).getBytes(StandardCharsets.UTF_8),
				parts[3].getBytes(StandardCharsets.UTF_8))) {
			// Constant time: a comparison that returns early leaks how much of a guess was right, which is
			// all an attacker needs to find the rest one byte at a time.
			return Optional.empty();
		}
		try {
			if (Instant.ofEpochSecond(Long.parseLong(parts[2])).isBefore(clock.instant())) {
				return Optional.empty();
			}
			return Optional.of(UUID.fromString(parts[1]));
		} catch (IllegalArgumentException malformed) {   // NumberFormatException is one of these
			// Only reachable for a payload we signed ourselves, so this is a bug rather than an attack —
			// and still not a reason to let the request through.
			return Optional.empty();
		}
	}

	/** The {@code Set-Cookie} §7 describes: httpOnly, Lax, Secure, and the configured domain. */
	public ResponseCookie asCookie(UUID quoteRequestId) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(NAME, mint(quoteRequestId))
				// Not readable from JavaScript: a session an injected script can read is a session it can
				// post somewhere else.
				.httpOnly(true)
				// Lax rather than Strict: the SMS handoff (§7's resume link) arrives as a top-level
				// navigation from another site, and Strict would drop the cookie exactly there.
				.sameSite("Lax")
				.secure(true)
				.path("/")
				.maxAge(ttl);
		if (!domain.isEmpty()) {
			builder.domain(domain);
		}
		return builder.build();
	}

	private String sign(String payload) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(key, ALGORITHM));
			return Base64.getUrlEncoder().withoutPadding()
					.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.GeneralSecurityException impossible) {
			throw new IllegalStateException("HmacSHA256 is required by the platform", impossible);
		}
	}
}
