package com.burakpadr.decorating.quoting.domain.event;

import com.burakpadr.decorating.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when an operator approves a quote and it reaches the customer.
 *
 * <p>Goes through the outbox: the subscriber sends an SMS, and a lost SMS is a lost sale.
 */
public record QuoteSent(
		UUID quoteRequestId,
		UUID quoteId,
		Instant occurredAt
) implements DomainEvent {

	public static final String TYPE = "QUOTE_SENT";

	@Override
	public UUID aggregateId() {
		return quoteRequestId;
	}

	@Override
	public String eventType() {
		return TYPE;
	}
}
