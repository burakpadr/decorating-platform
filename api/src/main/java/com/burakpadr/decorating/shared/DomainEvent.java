package com.burakpadr.decorating.shared;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for anything a module publishes to the rest of the system.
 *
 * <p>Events are the only integration seam between modules (§2.4). A module may import another
 * module's events and nothing else — {@code ArchitectureRulesTest} enforces exactly that, so an
 * event must live in its module's {@code domain.event} package to be importable.
 *
 * <p>Plain Java: no Spring, no Jackson, no JPA. Serialisation for the outbox happens in the
 * adapter layer.
 */
public interface DomainEvent {

	/** Identity of the thing the event is about — used as the outbox aggregate key. */
	UUID aggregateId();

	/** When the event occurred, UTC. */
	Instant occurredAt();

	/**
	 * Stable name written to {@code outbox.event_type} and used by subscribers. Deliberately not
	 * derived from the class name: renaming a class must not orphan rows already in the outbox.
	 */
	String eventType();
}
