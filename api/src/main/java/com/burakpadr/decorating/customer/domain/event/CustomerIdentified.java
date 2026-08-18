package com.burakpadr.decorating.customer.domain.event;

import com.burakpadr.decorating.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a phone number is verified and a customer row exists. Future subscriber:
 * {@code analytics}.
 */
public record CustomerIdentified(
		UUID customerId,
		Instant occurredAt
) implements DomainEvent {

	public static final String TYPE = "CUSTOMER_IDENTIFIED";

	@Override
	public UUID aggregateId() {
		return customerId;
	}

	@Override
	public String eventType() {
		return TYPE;
	}
}
