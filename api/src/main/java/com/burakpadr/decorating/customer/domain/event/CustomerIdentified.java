package com.burakpadr.decorating.customer.domain.event;

import com.burakpadr.decorating.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a phone number is verified and a customer row exists.
 *
 * <p>Carries the quote request that caused it as well as the customer, because the answer travels
 * back: {@code quoting} holds {@code quote_request.customer_id} and has no other way to learn what to
 * put in it — the module boundary permits events and nothing else (decision 0019). Both are ids, which
 * is all §2.4's fifth rule allows an event to carry.
 *
 * <p>Future subscriber: {@code analytics}.
 */
public record CustomerIdentified(
		UUID customerId,
		UUID quoteRequestId,
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
