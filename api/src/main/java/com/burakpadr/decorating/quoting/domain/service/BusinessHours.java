package com.burakpadr.decorating.quoting.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * When the business will have got back to you (§8, workflow §3.2, BOYA-46).
 *
 * <p>§3.2 states the rule as a lie to avoid: "Gece 23:00'te gelen talebe '2 saat içinde' demek yalan
 * olur; 'yarın sabah 10:00'a kadar' denir." A promise made at midnight against a clock nobody is
 * watching is not a promise, and the customer finds that out by waiting.
 *
 * <p>Pure, with no Spring and no database, for the reason {@code PricingEngine} is: a rule about time
 * that can only be exercised by waiting is a rule nobody tests. Every input is an argument.
 *
 * <p>What it does not know: holidays, and whether the business works at the weekend. Neither is
 * configured, and §16 still has "working hours / SLA window" down as an open question for the
 * business. Guessing a five-day week here would produce a promise that is wrong every Saturday, so
 * every day is a working day until somebody says otherwise — which errs towards promising sooner, and
 * a promise kept early costs nothing.
 */
public final class BusinessHours {

	private final ZoneId zone;
	private final LocalTime opens;
	private final LocalTime closes;
	private final Duration sla;

	public BusinessHours(ZoneId zone, LocalTime opens, LocalTime closes, Duration sla) {
		if (zone == null || opens == null || closes == null || sla == null) {
			throw new IllegalArgumentException("business hours are a zone, a window and a promise");
		}
		if (!closes.isAfter(opens)) {
			throw new IllegalArgumentException("a working day that ends before it starts is not one");
		}
		if (sla.isNegative() || sla.isZero()) {
			throw new IllegalArgumentException("a promise of no time at all is not a promise");
		}
		this.zone = zone;
		this.opens = opens;
		this.closes = closes;
		this.sla = sla;
	}

	/**
	 * The instant by which the answer will have arrived.
	 *
	 * <p>Working time is counted, not wall-clock time. A request at 17:30 with a two-hour promise does
	 * not become "19:30" because the office shut at 18:00 — it carries the remaining hour and a half
	 * into tomorrow morning. The alternative, adding the whole promise to the next opening, would
	 * quietly punish everyone who arrives late in the afternoon.
	 */
	public Instant promiseFrom(Instant now) {
		ZonedDateTime cursor = withinHours(now.atZone(zone));
		Duration left = sla;
		while (true) {
			ZonedDateTime closing = cursor.with(closes);
			Duration today = Duration.between(cursor, closing);
			if (today.compareTo(left) >= 0) {
				return cursor.plus(left).toInstant();
			}
			left = left.minus(today);
			cursor = cursor.toLocalDate().plusDays(1).atStartOfDay(zone).with(opens);
		}
	}

	/** Whether the business is open at this instant — what decides between "within" and "by". */
	public boolean openAt(Instant now) {
		LocalTime local = now.atZone(zone).toLocalTime();
		return !local.isBefore(opens) && local.isBefore(closes);
	}

	/** The next moment work can be done: now, or the next opening. */
	private ZonedDateTime withinHours(ZonedDateTime at) {
		LocalTime local = at.toLocalTime();
		if (local.isBefore(opens)) {
			return at.with(opens);
		}
		if (!local.isBefore(closes)) {
			LocalDate tomorrow = at.toLocalDate().plusDays(1);
			return tomorrow.atStartOfDay(zone).with(opens);
		}
		return at;
	}
}
