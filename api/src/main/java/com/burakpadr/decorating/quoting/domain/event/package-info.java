/**
 * Events published by the quoting module — the module's public surface to the rest of the system.
 *
 * <p>This is the one package inside {@code quoting} that other modules are allowed to import.
 * Everything else in {@code quoting} is private to it, and {@code ArchitectureRulesTest} fails the
 * build on any other cross-module reference.
 *
 * <p>Consequences of that, both deliberate:
 *
 * <ul>
 *   <li>An event may only expose {@code shared} types and primitives. Referencing
 *       {@code quoting.domain.model.Quote} from an event would drag the whole module across the
 *       boundary and defeat the point.
 *   <li>Events carry IDs, not objects. A subscriber that needs more calls back through the
 *       publishing module's inbound ports.
 * </ul>
 *
 * <p>Publish these from day one even where nothing subscribes (§2.4). {@code QuoteAccepted} is the
 * important one: today the only subscriber creates a callback task, and when {@code scheduling}
 * arrives it subscribes to the same event without {@code quoting} changing by a single line.
 */
package com.burakpadr.decorating.quoting.domain.event;
