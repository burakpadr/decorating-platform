package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.port.out.OtpCodes;
import com.burakpadr.decorating.shared.PhoneNumber;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code otp_code} rows (V8).
 *
 * <p>"Live" is the schema's own definition — neither consumed nor superseded — which is what
 * {@code otp_code_live_idx} is a partial unique index on. Asking the same question here and there is
 * deliberate: the index refuses a second live code even if this class forgets to retire the first.
 */
@Component
class OtpCodePersistenceAdapter implements OtpCodes {

	private static final RowMapper<LiveCode> AS_LIVE_CODE = (row, index) -> new LiveCode(
			row.getObject("id", UUID.class),
			row.getString("code_hash"),
			row.getTimestamp("expires_at").toInstant(),
			row.getInt("attempts"));

	private final JdbcTemplate jdbc;

	OtpCodePersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void supersedeLive(UUID quoteRequestId, Instant when) {
		jdbc.update("""
				UPDATE otp_code SET superseded_at = ?
				WHERE quote_request_id = ? AND consumed_at IS NULL AND superseded_at IS NULL
				""", Timestamp.from(when), quoteRequestId);
	}

	@Override
	public void issue(UUID id, UUID quoteRequestId, PhoneNumber phone, String codeHash,
			Instant expiresAt) {
		jdbc.update("""
				INSERT INTO otp_code (id, quote_request_id, phone, code_hash, expires_at)
				VALUES (?, ?, ?, ?, ?)
				""", id, quoteRequestId, phone.e164(), codeHash, Timestamp.from(expiresAt));
	}

	@Override
	public Optional<LiveCode> findLive(UUID quoteRequestId) {
		List<LiveCode> rows = jdbc.query("""
				SELECT id, code_hash, expires_at, attempts FROM otp_code
				WHERE quote_request_id = ? AND consumed_at IS NULL AND superseded_at IS NULL
				""", AS_LIVE_CODE, quoteRequestId);
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
	}

	/**
	 * Its own transaction, and the whole rule depends on it.
	 *
	 * <p>A wrong guess ends in a thrown refusal, which rolls the verification back — and would roll the
	 * increment back with it. The counter would then sit at zero however many times the code was
	 * guessed, and §11's "lock after 5" would never fire. Found by the fifth guess not locking.
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int recordFailedAttempt(UUID codeId) {
		Integer attempts = jdbc.queryForObject("""
				UPDATE otp_code SET attempts = attempts + 1 WHERE id = ? RETURNING attempts
				""", Integer.class, codeId);
		return attempts == null ? 0 : attempts;
	}

	@Override
	public void consume(UUID codeId, Instant when) {
		jdbc.update("UPDATE otp_code SET consumed_at = ? WHERE id = ?", Timestamp.from(when), codeId);
	}
}
