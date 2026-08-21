package com.burakpadr.decorating.quoting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stage 1's eight questions as they accumulate (§2.1, BOYA-25).
 *
 * <p>The form is three screens and the answers arrive a few at a time, so the interesting behaviour is
 * not holding them — it is merging. A merge that drops an answer costs the customer a screen and shows
 * up as an abandoned request, and a merge that overwrites one with nothing is worse: the estimate is
 * computed from whatever survived and nobody sees the gap.
 */
class StageOneAnswersTest {

	private static final StageOneAnswers FIRST_SCREEN = StageOneAnswers.empty()
			.mergedWith(new StageOneAnswers("KADIKOY", new BigDecimal("92"), AreaBasis.NET,
					Layout.THREE_PLUS_ONE, null, null, null, null, null));

	@Test
	@DisplayName("an answer from a later screen joins the ones already given")
	void laterAnswersJoinEarlierOnes() {
		StageOneAnswers both = FIRST_SCREEN.mergedWith(new StageOneAnswers(
				null, null, null, null, QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, null, null, null));

		assertThat(both.districtCode()).isEqualTo("KADIKOY");
		assertThat(both.layout()).isEqualTo(Layout.THREE_PLUS_ONE);
		assertThat(both.scope()).isEqualTo(QuoteScope.WHOLE_HOME);
		assertThat(both.furnishing()).isEqualTo(Furnishing.FURNISHED);
	}

	@Test
	@DisplayName("an absent field leaves what was already there — every field, not just the ones I thought of")
	void absentFieldsChangeNothing() {
		StageOneAnswers full = StageOneAnswers.empty().mergedWith(new StageOneAnswers(
				"USKUDAR", new BigDecimal("112"), AreaBasis.GROSS, Layout.FOUR_PLUS_ONE,
				QuoteScope.SELECTED_ROOMS, Furnishing.PARTIAL, 6, true, WallCondition.MAJOR));

		assertThat(full.mergedWith(StageOneAnswers.empty()))
				.as("a PATCH that sends nothing is a PATCH that changes nothing, field by field")
				.isEqualTo(full);
	}

	@Test
	@DisplayName("an answer can be corrected: the customer goes back and picks a different one")
	void anAnswerCanBeChanged() {
		StageOneAnswers corrected = FIRST_SCREEN.mergedWith(new StageOneAnswers(
				null, new BigDecimal("104"), null, Layout.FOUR_PLUS_ONE, null, null, null, null, null));

		assertThat(corrected.areaInput()).isEqualByComparingTo("104");
		assertThat(corrected.layout()).isEqualTo(Layout.FOUR_PLUS_ONE);
		assertThat(corrected.districtCode())
				.as("changing the layout does not un-answer the district")
				.isEqualTo("KADIKOY");
	}

	@Test
	@DisplayName("false and zero are answers, not absences")
	void falseAndZeroSurviveTheMerge() {
		StageOneAnswers withDoors = FIRST_SCREEN.mergedWith(new StageOneAnswers(
				null, null, null, null, null, null, 8, true, null));

		StageOneAnswers withoutDoors = withDoors.mergedWith(new StageOneAnswers(
				null, null, null, null, null, null, 0, false, null));

		// Boxed on purpose. A primitive int would make "no doors" indistinguishable from "did not say",
		// and a primitive boolean would make "no colour change" the same as silence — so the one customer
		// who says zero doors would be quietly given the last number they typed.
		assertThat(withoutDoors.doorCount()).isZero();
		assertThat(withoutDoors.doorColourChange()).isFalse();
	}

	@Test
	@DisplayName("an empty set of answers is complete about being empty")
	void emptyIsEmpty() {
		StageOneAnswers empty = StageOneAnswers.empty();

		assertThat(empty.districtCode()).isNull();
		assertThat(empty.areaInput()).isNull();
		assertThat(empty.areaBasis()).isNull();
		assertThat(empty.layout()).isNull();
		assertThat(empty.scope()).isNull();
		assertThat(empty.furnishing()).isNull();
		assertThat(empty.doorCount()).isNull();
		assertThat(empty.doorColourChange()).isNull();
		assertThat(empty.wallCondition()).isNull();
		assertThat(empty.isPriceable())
				.as("nothing to price yet, and saying so is what stops the estimate endpoint guessing")
				.isFalse();
	}

	@Test
	@DisplayName("priceable means every figure the engine needs is present")
	void priceableNeedsEveryEngineInput() {
		StageOneAnswers almost = StageOneAnswers.empty().mergedWith(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, null));

		assertThat(almost.isPriceable())
				.as("wall condition still missing, and §5.6 turns it into filler quantities — an estimate "
						+ "without it is not a cheaper estimate, it is a different job")
				.isFalse();
		assertThat(almost.mergedWith(new StageOneAnswers(
						null, null, null, null, null, null, null, null, WallCondition.MINOR))
				.isPriceable())
				.isTrue();
	}

	@Test
	@DisplayName("the door count is optional when doors are not being painted")
	void doorsCanBeLeftOut() {
		StageOneAnswers noDoors = StageOneAnswers.empty().mergedWith(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 0, false, WallCondition.GOOD));

		assertThat(noDoors.isPriceable()).isTrue();
		assertThat(noDoors.doorCount()).isZero();
	}
}
