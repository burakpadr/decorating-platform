package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.port.out.ResumeTokens;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code quote_request.resume_token} (§4.2).
 *
 * <p>128 bits from {@link SecureRandom}, base64url — 22 characters. It has to be unguessable: whoever
 * holds the token gets a session on somebody's draft, so a short or predictable one is an enumeration
 * attack on strangers' answers and photographs. 128 bits is not guessable, and the 21 characters it
 * saves over 256 are a whole UCS-2 segment on every message that carries the link
 * ({@code SmsSegmentBudgetTest} is where that shows up).
 */
@Component
class ResumeTokenPersistenceAdapter implements ResumeTokens {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int BYTES = 16;

	private final JdbcTemplate jdbc;
	private final Duration ttl;

	ResumeTokenPersistenceAdapter(JdbcTemplate jdbc,
			@Value("${decorating.quote.resume-token-ttl:P14D}") Duration ttl) {
		this.jdbc = jdbc;
		this.ttl = ttl;
	}

	@Override
	public String issueFor(UUID quoteRequestId) {
		String existing = jdbc.query(
				"SELECT resume_token FROM quote_request "
						+ "WHERE id = ? AND resume_token IS NOT NULL AND resume_token_expires > now()",
				row -> row.next() ? row.getString(1) : null, quoteRequestId);
		if (existing != null) {
			// Idempotent on purpose: a second SMS must not break the link in the first one, and the
			// customer usually taps the older message.
			return existing;
		}

		byte[] entropy = new byte[BYTES];
		RANDOM.nextBytes(entropy);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
		int updated = jdbc.update(
				"UPDATE quote_request SET resume_token = ?, resume_token_expires = now() + ?::interval, "
						+ "updated_at = now() WHERE id = ?",
				token, ttl.toDays() + " days", quoteRequestId);
		if (updated != 1) {
			throw new IllegalStateException("no quote_request " + quoteRequestId + " to issue a token for");
		}
		return token;
	}

	@Override
	public Optional<UUID> resolve(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		return jdbc.query(
				"SELECT id FROM quote_request WHERE resume_token = ? AND resume_token_expires > now()",
				row -> row.next() ? Optional.of(row.getObject(1, UUID.class)) : Optional.<UUID>empty(),
				token);
	}
}
