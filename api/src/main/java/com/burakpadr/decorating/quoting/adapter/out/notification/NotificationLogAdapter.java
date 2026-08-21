package com.burakpadr.decorating.quoting.adapter.out.notification;

import com.burakpadr.decorating.quoting.domain.model.TemplateCode;
import com.burakpadr.decorating.quoting.domain.port.out.NotificationLog;
import com.burakpadr.decorating.shared.PhoneNumber;
import com.burakpadr.decorating.shared.Uuid7;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** {@code notification} rows (§4). */
@Component
class NotificationLogAdapter implements NotificationLog {

	private final JdbcTemplate jdbc;

	NotificationLogAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void record(UUID quoteRequestId, TemplateCode template, PhoneNumber recipient,
			Optional<String> providerRef) {
		// QUEUED with no sent_at when nothing sent it. The three columns say the same thing three ways on
		// purpose: a row that claimed SENT with no provider_ref would be a message the business believes
		// it delivered.
		jdbc.update("""
				INSERT INTO notification (id, quote_request_id, channel, template_code, recipient, status,
				  provider_ref, sent_at)
				VALUES (?, ?, 'SMS', ?, ?, ?, ?, ?)
				""",
				Uuid7.generate(),
				quoteRequestId,
				template.name(),
				recipient.e164(),
				providerRef.isPresent() ? "SENT" : "QUEUED",
				providerRef.orElse(null),
				providerRef.isPresent() ? java.time.OffsetDateTime.now() : null);
	}
}
