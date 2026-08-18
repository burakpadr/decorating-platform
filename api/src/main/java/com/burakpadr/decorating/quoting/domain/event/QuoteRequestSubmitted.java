package com.burakpadr.decorating.quoting.domain.event;

import com.burakpadr.decorating.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when the customer submits a request with all required photos and a verified phone.
 * Future subscriber: {@code analytics}.
 */
public record QuoteRequestSubmitted(
		UUID quoteRequestId,
		Instant occurredAt
) implements DomainEvent {

	public static final String TYPE = "QUOTE_REQUEST_SUBMITTED";

	@Override
	public UUID aggregateId() {
		return quoteRequestId;
	}

	@Override
	public String eventType() {
		return TYPE;
	}
}
