package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.port.out.VerifiedPhoneWriter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The three columns §4.1 reserves for a verification.
 *
 * <p>Written straight rather than through the aggregate on purpose. {@code QuoteRequest} is the
 * status machine and none of these is a status: a verified phone does not move a request, it is a
 * fact recorded against one — the same shape as {@code acceptsPhotographs}, and the same reason
 * consent is not a transition either.
 */
@Component
class VerifiedPhonePersistenceAdapter implements VerifiedPhoneWriter {

	private final JdbcTemplate jdbc;

	VerifiedPhonePersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public java.util.Optional<Instant> verifiedAt(UUID quoteRequestId) {
		return jdbc.query("SELECT phone_verified_at FROM quote_request WHERE id = ?",
				(row, index) -> row.getTimestamp(1), quoteRequestId).stream()
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.map(java.sql.Timestamp::toInstant);
	}

	@Override
	public void recordVerified(UUID quoteRequestId, Instant verifiedAt) {
		jdbc.update("UPDATE quote_request SET phone_verified_at = ?, updated_at = now() WHERE id = ?",
				Timestamp.from(verifiedAt), quoteRequestId);
	}

	@Override
	public void attachCustomer(UUID quoteRequestId, UUID customerId) {
		// pending_phone goes here and nowhere else: the schema's comment calls it "pre-verification
		// contact (moved to customer on verify, then nulled)", and the number now lives on a row that
		// owns it.
		jdbc.update("""
				UPDATE quote_request SET customer_id = ?, pending_phone = NULL, updated_at = now()
				WHERE id = ?
				""", customerId, quoteRequestId);
	}
}
