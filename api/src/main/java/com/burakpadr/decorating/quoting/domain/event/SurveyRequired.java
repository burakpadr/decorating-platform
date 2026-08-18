package com.burakpadr.decorating.quoting.domain.event;

import com.burakpadr.decorating.shared.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when confidence is too low or a risk finding was detected, so a human must visit.
 * Current subscriber creates a {@code callback_task}; {@code scheduling} will subscribe later.
 */
public record SurveyRequired(
		UUID quoteRequestId,
		Instant occurredAt
) implements DomainEvent {

	public static final String TYPE = "SURVEY_REQUIRED";

	@Override
	public UUID aggregateId() {
		return quoteRequestId;
	}

	@Override
	public String eventType() {
		return TYPE;
	}
}
