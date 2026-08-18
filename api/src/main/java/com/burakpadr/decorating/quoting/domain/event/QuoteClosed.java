package com.burakpadr.decorating.quoting.domain.event;

import com.burakpadr.decorating.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a request reaches a terminal outcome.
 *
 * <p>Goes through the outbox: the subscriber schedules photo deletion, and silently losing that
 * leaves customer photographs on disk past their retention window.
 */
public record QuoteClosed(
		UUID quoteRequestId,
		Instant occurredAt
) implements DomainEvent {

	public static final String TYPE = "QUOTE_CLOSED";

	@Override
	public UUID aggregateId() {
		return quoteRequestId;
	}

	@Override
	public String eventType() {
		return TYPE;
	}
}
