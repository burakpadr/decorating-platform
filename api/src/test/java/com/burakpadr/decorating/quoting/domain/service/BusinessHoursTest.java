package com.burakpadr.decorating.quoting.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The promise made on the waiting screen (§8, workflow §3.2, BOYA-46).
 *
 * <p>§3.2 gives the rule as the lie it exists to prevent: telling a customer who submits at 23:00
 * "within 2 hours" is false, and they find that out by waiting for it. So every case here is a clock
 * time, and the interesting ones are all outside the working day.
 *
 * <p>No Spring and no database on purpose. A rule about time that can only be exercised by waiting is
 * a rule nobody tests, and this one is read by a customer deciding whether to put their phone down.
 */
class BusinessHoursTest {

	private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

	private final BusinessHours hours = new BusinessHours(
			ISTANBUL, LocalTime.of(9, 0), LocalTime.of(18, 0), Duration.ofHours(2));

	/** A local wall-clock time in Istanbul, as an instant. */
	private static Instant at(String isoLocal) {
		return java.time.LocalDateTime.parse(isoLocal).atZone(ISTANBUL).toInstant();
	}

	@Test
	@DisplayName("mid-morning, the promise is simply two hours away")
	void withinTheDay() {
		assertThat(hours.promiseFrom(at("2026-09-01T10:00:00")))
				.isEqualTo(at("2026-09-01T12:00:00"));
	}

	@Test
	@DisplayName("§3.2: at 23:00 the answer is tomorrow morning, not two hours from now")
	void atNight() {
		assertThat(hours.promiseFrom(at("2026-09-01T23:00:00")))
				.as("the sentence §3.2 refuses to let the system say is 'within 2 hours'")
				.isEqualTo(at("2026-09-02T11:00:00"));
	}

	@Test
	@DisplayName("before opening, the clock starts at opening")
	void beforeOpening() {
		assertThat(hours.promiseFrom(at("2026-09-01T06:30:00")))
				.isEqualTo(at("2026-09-01T11:00:00"));
	}

	@Test
	@DisplayName("late afternoon carries the remainder into the next morning, not the whole promise")
	void spillsOverTheEndOfTheDay() {
		// 17:30 leaves half an hour of today; the hour and a half left resumes at nine.
		assertThat(hours.promiseFrom(at("2026-09-01T17:30:00")))
				.as("adding the whole two hours to tomorrow's opening would punish everybody who "
						+ "arrives late in the afternoon, for no reason the customer could see")
				.isEqualTo(at("2026-09-02T10:30:00"));
	}

	@Test
	@DisplayName("a promise longer than a working day spans several of them")
	void spansMoreThanOneDay() {
		BusinessHours slow = new BusinessHours(
				ISTANBUL, LocalTime.of(9, 0), LocalTime.of(18, 0), Duration.ofHours(20));

		assertThat(slow.promiseFrom(at("2026-09-01T10:00:00")))
				.isEqualTo(at("2026-09-03T12:00:00"));
	}

	@Test
	@DisplayName("exactly at closing time the day is over")
	void closingIsExclusive() {
		assertThat(hours.openAt(at("2026-09-01T18:00:00"))).isFalse();
		assertThat(hours.openAt(at("2026-09-01T17:59:00"))).isTrue();
		assertThat(hours.openAt(at("2026-09-01T09:00:00"))).isTrue();
		assertThat(hours.openAt(at("2026-09-01T08:59:00"))).isFalse();
	}

	@Test
	@DisplayName("the promise is an instant, so the screen renders it in the customer's own clock")
	void carriesTheZoneRatherThanAString() {
		// The database keeps timestamptz in UTC and Europe/Istanbul is a presentation concern (§4).
		assertThat(hours.promiseFrom(at("2026-09-01T10:00:00")))
				.isEqualTo(Instant.parse("2026-09-01T09:00:00Z"));
	}

	@Test
	@DisplayName("nonsense hours are refused where they are configured, not where they are read")
	void refusesImpossibleHours() {
		assertThatThrownBy(() -> new BusinessHours(ISTANBUL, LocalTime.of(18, 0), LocalTime.of(9, 0),
				Duration.ofHours(2))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new BusinessHours(ISTANBUL, LocalTime.of(9, 0), LocalTime.of(18, 0),
				Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
	}
}
