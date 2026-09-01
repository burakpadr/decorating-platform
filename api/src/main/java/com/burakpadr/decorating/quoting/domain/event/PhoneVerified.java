package com.burakpadr.decorating.quoting.domain.event;

import com.burakpadr.decorating.shared.DomainEvent;
import com.burakpadr.decorating.shared.PhoneNumber;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a customer proves a phone number belongs to them (workflow §3.1).
 *
 * <p>Subscriber: {@code customer}, which creates or finds the row §4.1 says may not exist before this
 * moment. It carries the number because that is the customer module's lookup key and there is no
 * other way for it to learn one — {@code PhoneNumber} is a {@code shared} value object, which is what
 * makes it legal cargo for an event (§2.4's fifth rule).
 */
public record PhoneVerified(
		UUID quoteRequestId,
		PhoneNumber phone,
		Instant occurredAt
) implements DomainEvent {

	public static final String TYPE = "PHONE_VERIFIED";

	@Override
	public UUID aggregateId() {
		return quoteRequestId;
	}

	@Override
	public String eventType() {
		return TYPE;
	}
}
