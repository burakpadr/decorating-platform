package com.burakpadr.decorating.quoting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Naming the version a bulk increase produces.
 *
 * <p>§7's bulk-increase body carries a target and a percent and no version code, so the code has to
 * be derived. The operator does one of these every quarter (workflow §6) and should not have to invent
 * a name for it — but the name still has to be readable months later, when a customer turns up with a
 * quote priced against one of them.
 */
class PriceBookVersionCodeTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
		"REAL-2026-01,REAL-2026-02",
		"REAL-2026-09,REAL-2026-10",
		"REAL-2026-99,REAL-2026-100",
		"SEED-2026-1,SEED-2026-2"})
	@DisplayName("the trailing number advances and keeps its width")
	void advancesTheTrailingNumber(String from, String expected) {
		assertThat(PriceBookVersionCode.next(from)).isEqualTo(expected);
	}

	@Test
	@DisplayName("a code with no trailing number gets one rather than being overwritten")
	void appendsWhereThereIsNoNumber() {
		assertThat(PriceBookVersionCode.next("REAL")).isEqualTo("REAL-2");
		assertThat(PriceBookVersionCode.next("REAL-FINAL")).isEqualTo("REAL-FINAL-2");
	}

	@Test
	@DisplayName("the result is a legal version code, so it can be used without further checks")
	void staysWithinTheColumn() {
		String next = PriceBookVersionCode.next("REAL-2026-01");

		assertThat(next).matches("[A-Z0-9-]+").hasSizeLessThanOrEqualTo(32);
	}
}
