package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.Consent;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import com.burakpadr.decorating.quoting.domain.port.out.ConsentRepository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code consent} rows (§4.7).
 *
 * <p>Insert only. The table has no unique key on {@code (quote_request_id, consent_type)} and this
 * adapter does not invent one: §12's record is the sequence of decisions, so a change of mind is a
 * second row and {@link #latest} is what reads the answer back.
 *
 * <p>{@code ip_address} is left null. The application sits behind Caddy, so the address the servlet
 * container reports is the proxy's; writing that would fill a column meant for evidence with a
 * constant. Populating it honestly needs a decision about {@code X-Forwarded-For} that no other part of
 * this codebase has had to make yet.
 */
@Component
class ConsentPersistenceAdapter implements ConsentRepository {

	private final JdbcTemplate jdbc;

	ConsentPersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(Consent consent) {
		jdbc.update("""
				INSERT INTO consent (id, quote_request_id, consent_type, granted, text_version, created_at)
				VALUES (?, ?, ?, ?, ?, ?)
				""",
				consent.id(), consent.quoteRequestId(), consent.type().name(), consent.granted(),
				consent.textVersion(), Timestamp.from(consent.recordedAt()));
	}

	@Override
	public Optional<Consent> latest(UUID quoteRequestId, ConsentType type) {
		return jdbc.query("""
				SELECT id, quote_request_id, consent_type, granted, text_version, created_at
				FROM consent
				WHERE quote_request_id = ? AND consent_type = ? AND revoked_at IS NULL
				ORDER BY created_at DESC, id DESC
				LIMIT 1
				""", (row, index) -> new Consent(
						row.getObject("id", UUID.class),
						row.getObject("quote_request_id", UUID.class),
						ConsentType.valueOf(row.getString("consent_type")),
						row.getBoolean("granted"),
						row.getString("text_version"),
						row.getTimestamp("created_at").toInstant()),
				quoteRequestId, type.name()).stream().findFirst();
	}
}
