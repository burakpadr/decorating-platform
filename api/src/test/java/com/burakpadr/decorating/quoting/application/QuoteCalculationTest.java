package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculation;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculationCommand;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.CalculateQuote;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Pricing a job the operator types in by hand (workflow §12, increment 1).
 *
 * <p>This is the increment's whole deliverable: the business enters a job it already knows the price
 * of and compares. Nothing else in increment 1 can be checked against reality without it — the engine
 * has tests, but a test cannot tell you the figures are the ones this business would charge.
 *
 * <p>Against the seeded price book rather than a fixture, because what is being asked is "what would
 * the live list quote for this", and a fixture would answer for a list nobody is quoting from.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class QuoteCalculationTest {

	@Autowired
	private CalculateQuote calculator;

	@Test
	@DisplayName("§5.10's job, priced against the live list: 3+1, 92 m² net, furnished, 8 doors")
	void pricesTheWorkedExampleAgainstTheLiveList() {
		QuoteCalculation result = calculator.calculate(new QuoteCalculationCommand(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Set.of(), WallCondition.MINOR, Furnishing.FURNISHED,
				8, true, false, true, false));

		// §5.10's job against the reconciled book (V5), Kadıköy at 1.0000. The figure is not §5.10's:
		// that example's arithmetic belongs to the seed's item costs, which PricingEngineTest still holds
		// to the letter. What this asserts is that the live list prices the worked example's shape.
		assertThat(result.quote().totalCost()).isEqualByComparingTo("32955.51");
		assertThat(result.quote().billableDays())
				.as("two painters, not three (V6): the same work, half again as many days, same money")
				.isEqualTo(5);
		assertThat(result.priceBookVersion()).isEqualTo("REAL-2026-03");

		assertThat(result.netArea())
				.as("a net area is used as it stands")
				.isEqualByComparingTo("92.00");
		assertThat(result.areaWasGross()).isFalse();

		assertThat(result.rooms().size())
				.as("the seven areas §2.1 derives for a 3+1, so the operator can see what was assumed")
				.isEqualTo(7);
		assertThat(result.rooms().rooms()).extracting("label")
				.contains("Salon", "Ebeveyn yatak odası", "Yatak odası 1", "Mutfak", "Koridor");
		assertThat(result.quote().line(ItemCode.WALL_PAINT).quantity().doubleValue())
				.isCloseTo(220.83, org.assertj.core.api.Assertions.within(0.01));
	}

	@Test
	@DisplayName("a gross area is converted with the live list's ratio, and widens the band")
	void convertsAGrossAreaAndSaysSo() {
		QuoteCalculation result = calculator.calculate(new QuoteCalculationCommand(
				"KADIKOY", new BigDecimal("112"), AreaBasis.GROSS, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Set.of(), WallCondition.MINOR, Furnishing.FURNISHED,
				8, true, false, true, false));

		assertThat(result.netArea())
				.as("112 × 0.82, the ratio the active version carries — not a constant in the code")
				.isEqualByComparingTo("91.84");
		assertThat(result.areaWasGross()).isTrue();
		assertThat(result.quote().bandRatio())
				.as("§5.9 pays for the conversion with 5 points of band, because a gross area is an "
						+ "assumption about the walls")
				.isEqualByComparingTo("0.17");
	}

	@Test
	@DisplayName("UNSURE walls widen the band without moving the price")
	void unsureWallsWidenTheBandOnly() {
		QuoteCalculationCommand minor = new QuoteCalculationCommand(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Set.of(), WallCondition.MINOR, Furnishing.EMPTY,
				0, false, false, true, false);
		QuoteCalculation confident = calculator.calculate(minor);
		QuoteCalculation unsure = calculator.calculate(new QuoteCalculationCommand(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Set.of(), WallCondition.UNSURE, Furnishing.EMPTY,
				0, false, false, true, false));

		assertThat(unsure.quote().bandRatio()).isEqualByComparingTo("0.27");
		assertThat(confident.quote().bandRatio()).isEqualByComparingTo("0.12");
		assertThat(unsure.quote().bandLow()).isLessThan(confident.quote().bandLow());
		assertThat(unsure.quote().bandHigh()).isGreaterThan(confident.quote().bandHigh());
	}

	@Test
	@DisplayName("painting only some rooms prices only those")
	void pricesSelectedRoomsOnly() {
		QuoteCalculation result = calculator.calculate(new QuoteCalculationCommand(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.SELECTED_ROOMS, Set.of(RoomType.LIVING_ROOM, RoomType.BEDROOM),
				WallCondition.GOOD, Furnishing.EMPTY, 0, false, false, true, false));

		assertThat(result.rooms().size()).isEqualTo(3);
		assertThat(result.quote().line(ItemCode.MASKING).quantity()).isEqualByComparingTo("3");
	}

	@Test
	@DisplayName("a job with no rooms selected is refused rather than priced at nothing")
	void refusesAnEmptySelection() {
		assertThatThrownBy(() -> calculator.calculate(new QuoteCalculationCommand(
						"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.STUDIO,
						QuoteScope.SELECTED_ROOMS, Set.of(RoomType.STUDY), WallCondition.GOOD,
						Furnishing.EMPTY, 0, false, false, true, false)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
