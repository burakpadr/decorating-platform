package com.burakpadr.decorating.quoting.domain.event;

import com.burakpadr.decorating.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when the customer accepts a quote.
 *
 * <p>The important one. Today the only subscriber creates a {@code callback_task}; when
 * {@code scheduling} and {@code jobs} arrive they subscribe to this same event and {@code quoting}
 * does not change by a single line.
 */
public record QuoteAccepted(
		UUID quoteRequestId,
		UUID quoteId,
		Instant occurredAt
) implements DomainEvent {

	public static final String TYPE = "QUOTE_ACCEPTED";

	@Override
	public UUID aggregateId() {
		return quoteRequestId;
	}

	@Override
	public String eventType() {
		return TYPE;
	}
}
