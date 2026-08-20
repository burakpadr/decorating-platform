package com.burakpadr.decorating.quoting.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.burakpadr.decorating.quoting.domain.model.Coating;
import com.burakpadr.decorating.quoting.domain.model.FillerBand;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierTarget;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookItem;
import com.burakpadr.decorating.quoting.domain.model.PriceModifier;
import com.burakpadr.decorating.quoting.domain.model.PricedQuote;
import com.burakpadr.decorating.quoting.domain.model.PricingInput;
import com.burakpadr.decorating.quoting.domain.model.PricingSource;
import com.burakpadr.decorating.quoting.domain.model.QuoteLine;
import com.burakpadr.decorating.quoting.domain.model.RoomInput;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.RoomTypeConfig;
import com.burakpadr.decorating.quoting.domain.model.ServiceDistrict;
import com.burakpadr.decorating.quoting.domain.model.SurfaceInput;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The pricing engine (§5). Highest testing priority (§17) for a reason specific to this product: an
 * arithmetic error here is money lost on every quote it touches, silently, with no user-visible
 * symptom.
 *
 * <p>No Spring context, no database, nothing but the JDK — the purity rule
 * {@code ArchitectureRulesTest#pricingEngineIsPure} enforces exists so this suite runs in
 * milliseconds and can therefore be written first.
 *
 * <p>The fixture is §5.11's seed with Kadıköy at 1.05, which is what §5.10's worked example prices
 * against; the seeded migration has every district at 1.0000, so the example's district step only
 * reproduces with the factor set here. Cases that are not about the district use an unlisted code,
 * which prices at 1.0000 and keeps the arithmetic readable.
 */
class PricingEngineTest {

	private static final BigDecimal CENT = new BigDecimal("0.01");

	private final PricingEngine engine = new PricingEngine();

	// =============================================================================================
	// §5.10 — the worked example, as a regression fixture.
	//
	// Expected figures are the unrounded chain, not the section's table: §5.8 rounds at line total and
	// grand total only, while the table shows intermediate quantities rounded for reading (221 m²
	// rather than 220.83). Both are asserted — the exact values catch drift, the published figures
	// catch a misreading of the algorithm.
	// =============================================================================================

	@Test
	@DisplayName("§5.10: 3+1, 92 m² net, Kadıköy, furnished, 8 doors with a colour change, MINOR walls")
	void reproducesTheWorkedExample() {
		PricedQuote quote = engine.price(workedExample(), book());

		assertLine(quote.line(ItemCode.WALL_PAINT), "220.83", "17114.40", "8391.58", "25505.98");
		assertLine(quote.line(ItemCode.CEILING_PAINT), "92.00", "8050.00", "3496.00", "11546.00");
		assertLine(quote.line(ItemCode.PATCH_FILLING), "33.12", "2070.29", "496.87", "2567.16");
		assertLine(quote.line(ItemCode.DOOR_PAINT), "8.00", "5250.00", "1800.00", "7050.00");
		assertLine(quote.line(ItemCode.MASKING), "7.00", "1006.25", "434.00", "1440.25");
		assertLine(quote.line(ItemCode.MOBILIZATION), "1.00", "1900.00", "0.00", "1900.00");

		assertThat(quote.totalCost()).isEqualByComparingTo("52509.86");
		assertThat(quote.subtotalExVat()).isEqualByComparingTo("68262.82");
		assertThat(quote.vatAmount()).isEqualByComparingTo("13652.56");
		assertThat(quote.total()).isEqualByComparingTo("81915.39");

		assertThat(quote.totalMinutes()).isEqualByComparingTo("4116.85");
		assertThat(quote.billableDays()).isEqualTo(3);
		assertThat(quote.minimumCost()).isEqualByComparingTo("13500.00");
		assertThat(quote.minimumBinding()).isFalse();

		assertThat(quote.bandRatio()).isEqualByComparingTo("0.12");
		assertThat(quote.bandLow()).isEqualByComparingTo("72085.54");
		assertThat(quote.bandHigh()).isEqualByComparingTo("91745.23");
	}

	@Test
	@DisplayName("§5.10's published figures: total cost 52.520, ex-VAT 68.276, 3.293 minutes, 3 days")
	void agreesWithTheFiguresPrintedInTheSpec() {
		PricedQuote quote = engine.price(workedExample(), book());

		assertThat(quote.line(ItemCode.WALL_PAINT).quantity().doubleValue()).isCloseTo(221, within(1.0));
		assertThat(quote.line(ItemCode.CEILING_PAINT).quantity().doubleValue()).isCloseTo(92, within(0.5));
		assertThat(quote.totalCost().doubleValue()).isCloseTo(52_520, within(60.0));
		assertThat(quote.subtotalExVat().doubleValue()).isCloseTo(68_276, within(80.0));
		// The section prints minutes before the furnishing surcharge; the engine reports them after,
		// because that is what the crew actually spends. 3,293 × 1.25 = 4,116.
		assertThat(quote.totalMinutes().doubleValue()).isCloseTo(3_293 * 1.25, within(5.0));
		assertThat(quote.billableDays()).isEqualTo(3);
	}

	// =============================================================================================
	// §5.3, §5.4 — allocation and wall area, per room type.
	// =============================================================================================

	@ParameterizedTest(name = "{0}: 40 m² becomes {1} m² of paintable wall")
	@CsvSource({
		"LIVING_ROOM,61.6113", "MASTER_BEDROOM,61.6113", "BEDROOM,61.6113", "STUDY,61.6113",
		"KITCHEN,42.0009", "BATHROOM,12.6228", "HALLWAY,82.6493", "BALCONY,64.6167"})
	@DisplayName("§5.3–5.4: every room type uses its own perimeter factor and paintable ratio")
	void everyRoomTypeUsesItsOwnCoefficients(String type, String expectedWallArea) {
		PricedQuote quote = engine.price(
				stage1(new BigDecimal("40.00"), RoomInput.declared(RoomType.valueOf(type), WallCondition.GOOD)),
				book());

		assertThat(quote.line(ItemCode.WALL_PAINT).quantity())
				.isCloseTo(new BigDecimal(expectedWallArea), within(CENT));
		assertThat(quote.line(ItemCode.CEILING_PAINT).quantity())
				.as("one room takes the whole net area, whatever its weight")
				.isCloseTo(new BigDecimal("40.00"), within(CENT));
	}

	@Test
	@DisplayName("§5.4: a hallway's perimeter is 37% above the square-room assumption of 4.0")
	void hallwayWallsExceedTheSquareRoomAssumption() {
		BigDecimal hallway = engine
				.price(stage1(new BigDecimal("40.00"), RoomInput.declared(RoomType.HALLWAY, WallCondition.GOOD)), book())
				.line(ItemCode.WALL_PAINT).quantity();
		BigDecimal bedroom = engine
				.price(stage1(new BigDecimal("40.00"), RoomInput.declared(RoomType.BEDROOM, WallCondition.GOOD)), book())
				.line(ItemCode.WALL_PAINT).quantity();

		// 4.0 × √40 × 2.70 × 0.88 = 60.11 m². The hallway's 5.5 gives 82.65.
		BigDecimal squareAssumption = new BigDecimal("60.1086");
		assertThat(hallway.divide(squareAssumption, java.math.MathContext.DECIMAL64).doubleValue())
				.as("a 1.2 × 6.5 m hallway has far more wall than its area suggests; 4.0 underquotes it")
				.isCloseTo(1.375, within(0.005));
		assertThat(hallway).isGreaterThan(bedroom);
	}

	// =============================================================================================
	// §5.5 — deductions. The one place stage 1 and stage 2 are allowed to differ.
	// =============================================================================================

	@Test
	@DisplayName("§5.5: stage 2 counts the openings where stage 1 applies a flat 12%")
	void stageTwoCountsOpeningsWhereStageOneEstimatesThem() {
		BigDecimal declared = engine
				.price(stage1(new BigDecimal("40.00"), RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.GOOD)), book())
				.line(ItemCode.WALL_PAINT).quantity();
		BigDecimal counted = engine
				.price(stage2(new BigDecimal("40.00"),
						RoomInput.analysed(RoomType.LIVING_ROOM, List.of(clean("WALL_1")), 1, 1, 0, 0, false)), book())
				.line(ItemCode.WALL_PAINT).quantity();

		assertThat(declared).isCloseTo(new BigDecimal("61.6113"), within(CENT));
		assertThat(counted)
				.as("70.01 m² less one door at 1.90 and one window at 2.20")
				.isCloseTo(new BigDecimal("65.9128"), within(CENT));
		assertThat(counted).isNotEqualByComparingTo(declared);
	}

	@Test
	@DisplayName("§5.5: the 60% floor stops counted openings from deducting a wall away")
	void theSixtyPercentFloorHoldsWhenOpeningsAreImplausible() {
		PricedQuote quote = engine.price(
				stage2(new BigDecimal("12.00"),
						RoomInput.analysed(RoomType.LIVING_ROOM, List.of(clean("WALL_1")), 6, 4, 0, 0, false)),
				book());

		// 38.35 m² of wall, 20.20 m² of claimed openings: without the guard the room loses 53% of its
		// walls to doors it cannot physically hold.
		assertThat(quote.line(ItemCode.WALL_PAINT).quantity())
				.isCloseTo(new BigDecimal("23.0086"), within(CENT));
	}

	@Test
	@DisplayName("§5.5: a surface that is not painted is excluded entirely, not discounted")
	void aSurfaceThatIsNotPaintedIsExcludedEntirely() {
		PricedQuote quote = engine.price(
				stage2(new BigDecimal("40.00"),
						RoomInput.analysed(RoomType.LIVING_ROOM,
								List.of(clean("WALL_1"), tiled("WALL_2")), 0, 0, 0, 0, false)),
				book());

		assertThat(quote.line(ItemCode.WALL_PAINT).quantity())
				.as("a tiled wall is not a cheaper wall, it is not this job's wall")
				.isCloseTo(new BigDecimal("35.0064"), within(CENT));
	}

	// =============================================================================================
	// §5.6 — quantity sources.
	// =============================================================================================

	@Test
	@DisplayName("§5.6: each item code takes its quantity from its own source")
	void everyItemCodeTakesItsQuantityFromItsOwnSource() {
		SurfaceInput damaged = new SurfaceInput("WALL_1", Coating.PAINTED, Tone.DARK, FillerBand.FULL,
				true, Moisture.STAIN, true, new BigDecimal("0.90"));
		PricingInput input = new PricingInput("TEST", new BigDecimal("40.00"), false,
				List.of(RoomInput.analysed(RoomType.LIVING_ROOM, List.of(damaged, clean("WALL_2")),
						0, 2, 1, 3, true)),
				Furnishing.EMPTY, 5, false, false, true, false, PricingSource.STAGE_2);

		PricedQuote quote = engine.price(input, book());
		BigDecimal wall = quote.line(ItemCode.WALL_PAINT).quantity();
		BigDecimal oneSurface = wall.divide(new BigDecimal("2"));

		assertThat(quote.line(ItemCode.CEILING_PAINT).quantity())
				.as("ceiling area is the room area, with no deduction at all")
				.isCloseTo(new BigDecimal("40.00"), within(CENT));
		assertThat(quote.line(ItemCode.PATCH_FILLING).quantity())
				.as("FULL on one of two surfaces")
				.isCloseTo(oneSurface, within(CENT));
		assertThat(quote.line(ItemCode.SKIM_COAT).quantity()).isCloseTo(oneSurface, within(CENT));
		assertThat(quote.line(ItemCode.PRIMER).quantity())
				.as("primer follows the skim coat and a dark tone; here they are the same surface")
				.isCloseTo(oneSurface, within(CENT));
		assertThat(quote.line(ItemCode.STAIN_BLOCK_PRIMER).quantity()).isCloseTo(oneSurface, within(CENT));
		assertThat(quote.line(ItemCode.WALLPAPER_STRIPPING).quantity()).isCloseTo(oneSurface, within(CENT));
		assertThat(quote.line(ItemCode.DOOR_PAINT).quantity())
				.as("doors come from the customer's declared total, not from the room counts")
				.isEqualByComparingTo("5");
		assertThat(quote.line(ItemCode.TRIM_PAINT).quantity()).isEqualByComparingTo("2");
		assertThat(quote.line(ItemCode.RADIATOR_PAINT).quantity()).isEqualByComparingTo("1");
		assertThat(quote.line(ItemCode.DOWNLIGHT_CUTTING).quantity()).isEqualByComparingTo("3");
		assertThat(quote.line(ItemCode.CORNICE_CUTTING).quantity()).isEqualByComparingTo("1");
		assertThat(quote.line(ItemCode.MASKING).quantity()).isEqualByComparingTo("1");
		assertThat(quote.line(ItemCode.MOBILIZATION).quantity()).isEqualByComparingTo("1");
	}

	@ParameterizedTest(name = "filler band {0} covers {1} of the wall")
	@CsvSource({"NONE,0.00", "LOW,0.15", "MEDIUM,0.35", "HIGH,0.60", "FULL,1.00"})
	@DisplayName("§5.6: a filler band becomes a ratio of the surface area")
	void fillerBandsBecomeRatios(String band, String ratio) {
		SurfaceInput surface = new SurfaceInput("WALL_1", Coating.PAINTED, Tone.LIGHT,
				FillerBand.valueOf(band), false, Moisture.NONE, false, new BigDecimal("0.90"));
		PricedQuote quote = engine.price(
				stage2(new BigDecimal("40.00"),
						RoomInput.analysed(RoomType.LIVING_ROOM, List.of(surface), 0, 0, 0, 0, false)),
				book());

		BigDecimal expected = quote.line(ItemCode.WALL_PAINT).quantity().multiply(new BigDecimal(ratio));
		if (expected.signum() == 0) {
			assertThat(quote.hasLine(ItemCode.PATCH_FILLING))
					.as("a zero quantity is not a line; it is money charged for nothing")
					.isFalse();
		} else {
			assertThat(quote.line(ItemCode.PATCH_FILLING).quantity()).isCloseTo(expected, within(CENT));
		}
	}

	@ParameterizedTest(name = "{0} walls → filler {1}, skim {2}, primer follows the skim")
	@CsvSource({"GOOD,0.00,0.00", "MINOR,0.15,0.00", "MAJOR,0.40,0.25", "UNSURE,0.20,0.00"})
	@DisplayName("§5.6: a declared condition becomes synthetic findings on every surface")
	void declaredConditionBecomesFillerAndSkim(String condition, String filler, String skim) {
		PricedQuote quote = engine.price(
				stage1(new BigDecimal("30.00"),
						RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.valueOf(condition))),
				book());
		BigDecimal wall = quote.line(ItemCode.WALL_PAINT).quantity();

		assertQuantity(quote, ItemCode.PATCH_FILLING, wall.multiply(new BigDecimal(filler)));
		assertQuantity(quote, ItemCode.SKIM_COAT, wall.multiply(new BigDecimal(skim)));
		assertQuantity(quote, ItemCode.PRIMER, wall.multiply(new BigDecimal(skim)));
	}

	// =============================================================================================
	// §5.7 — modifiers, each in isolation.
	// =============================================================================================

	@Test
	@DisplayName("FURNISHED multiplies labour and leaves materials alone")
	void appliesFurnishedToLabourOnly() {
		QuoteLine before = engine.price(oneRoom(Furnishing.EMPTY, false), book()).line(ItemCode.WALL_PAINT);
		QuoteLine after = engine.price(oneRoom(Furnishing.FURNISHED, false), book()).line(ItemCode.WALL_PAINT);

		assertThat(after.materialCost())
				.as("a furnished home consumes the same paint; charging 25%% more for it overprices")
				.isEqualByComparingTo(before.materialCost());
		assertThat(after.labourCost())
				.isEqualByComparingTo(before.labourCost().multiply(new BigDecimal("1.25")));
	}

	@Test
	@DisplayName("PARTIAL furnishing costs half the furnished delta, not half the factor")
	void appliesHalfTheDeltaForPartialFurnishing() {
		BigDecimal empty = engine.price(oneRoom(Furnishing.EMPTY, false), book()).line(ItemCode.WALL_PAINT).labourCost();
		BigDecimal partial = engine.price(oneRoom(Furnishing.PARTIAL, false), book()).line(ItemCode.WALL_PAINT).labourCost();

		assertThat(partial).isEqualByComparingTo(empty.multiply(new BigDecimal("1.125")));
	}

	@Test
	@DisplayName("RUSH multiplies labour only, and compounds with the furnishing surcharge")
	void rushMultipliesLabourOnly() {
		QuoteLine plain = engine.price(oneRoom(Furnishing.EMPTY, false), book()).line(ItemCode.WALL_PAINT);
		QuoteLine rushed = engine.price(oneRoom(Furnishing.EMPTY, true), book()).line(ItemCode.WALL_PAINT);
		QuoteLine both = engine.price(oneRoom(Furnishing.FURNISHED, true), book()).line(ItemCode.WALL_PAINT);

		assertThat(rushed.labourCost()).isEqualByComparingTo(plain.labourCost().multiply(new BigDecimal("1.25")));
		assertThat(rushed.materialCost())
				.as("working weekends does not consume more paint")
				.isEqualByComparingTo(plain.materialCost());
		assertThat(both.labourCost())
				.as("§5.2's order compounds them: 1.25 × 1.25, not 1.50")
				.isEqualByComparingTo(plain.labourCost().multiply(new BigDecimal("1.5625")));
	}

	@Test
	@DisplayName("a door colour change applies DARK_TO_LIGHT to doors only, in stage 1")
	void appliesDarkToLightToDoorsOnlyInStageOne() {
		PricedQuote plain = engine.price(withDoors(8, false), book());
		PricedQuote changed = engine.price(withDoors(8, true), book());

		assertThat(changed.line(ItemCode.DOOR_PAINT).lineTotal())
				.isEqualByComparingTo(plain.line(ItemCode.DOOR_PAINT).lineTotal().multiply(new BigDecimal("1.50")));
		assertThat(changed.line(ItemCode.WALL_PAINT).lineTotal())
				.as("stage 1 has no surface tone, so walls cannot be dark and are not painted three times")
				.isEqualByComparingTo(plain.line(ItemCode.WALL_PAINT).lineTotal());
		assertThat(changed.totalMinutes())
				.as("a third coat takes longer, so the modifier scales minutes too (§5.8)")
				.isGreaterThan(plain.totalMinutes());
	}

	@Test
	@DisplayName("DARK_TO_LIGHT scales with the dark share of the walls, not with any dark wall at all")
	void darkToLightScalesWithTheDarkShareOfTheWalls() {
		BigDecimal light = engine.price(
						stage2(new BigDecimal("40.00"), RoomInput.analysed(RoomType.LIVING_ROOM,
								List.of(clean("WALL_1"), clean("WALL_2")), 0, 0, 0, 0, false)), book())
				.line(ItemCode.WALL_PAINT).lineTotal();
		BigDecimal halfDark = engine.price(
						stage2(new BigDecimal("40.00"), RoomInput.analysed(RoomType.LIVING_ROOM,
								List.of(dark("WALL_1"), clean("WALL_2")), 0, 0, 0, 0, false)), book())
				.line(ItemCode.WALL_PAINT).lineTotal();

		assertThat(halfDark)
				.as("half the wall area dark at a 1.50 factor is 1.25 on the line, not 1.50")
				.isCloseTo(light.multiply(new BigDecimal("1.25")), within(CENT));
	}

	@Test
	@DisplayName("no elevator multiplies mobilization and nothing else")
	void appliesNoElevatorToMobilizationOnly() {
		PricedQuote lift = engine.price(oneRoom(Furnishing.EMPTY, false), book());
		PricedQuote stairs = engine.price(oneRoomWithoutElevator(), book());

		assertThat(stairs.line(ItemCode.MOBILIZATION).lineTotal())
				.isEqualByComparingTo(lift.line(ItemCode.MOBILIZATION).lineTotal().multiply(new BigDecimal("1.20")));
		assertThat(stairs.line(ItemCode.WALL_PAINT).lineTotal())
				.isEqualByComparingTo(lift.line(ItemCode.WALL_PAINT).lineTotal());
	}

	@Test
	@DisplayName("mobilization sits outside the labour modifiers and outside the day count")
	void mobilizationStaysOutsideTheLabourModifiers() {
		PricedQuote empty = engine.price(oneRoom(Furnishing.EMPTY, false), book());
		PricedQuote furnished = engine.price(oneRoom(Furnishing.FURNISHED, false), book());

		assertThat(furnished.line(ItemCode.MOBILIZATION).labourCost())
				.as("§5.2 adds mobilization at step 9, after the labour modifiers of step 7")
				.isEqualByComparingTo(empty.line(ItemCode.MOBILIZATION).labourCost());

		BigDecimal withoutMobilization = minutesOf(empty);
		assertThat(empty.totalMinutes())
				.as("its 60 minutes are not in the duration either — §5.10's 3,293 excludes them")
				.isCloseTo(withoutMobilization, within(CENT));
	}

	// =============================================================================================
	// §5.8 — duration, minimum, margin, VAT.
	// =============================================================================================

	@Test
	@DisplayName("§5.8: 1.11 days bills 1, 1.40 days bills 2 — the 0.25 tolerance in both directions")
	void appliesTheDayRoundingToleranceInBothDirections() {
		// 118 m² of living room comes to 1.114 days, 157 m² to 1.398. The tolerance is what separates
		// them: without it both would bill 2, with a plain floor both would bill 1.
		PricedQuote justOver = engine.price(
				stage1(new BigDecimal("118.00"), RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.GOOD)), book());
		PricedQuote almostOneAndAHalf = engine.price(
				stage1(new BigDecimal("157.00"), RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.GOOD)), book());

		assertThat(justOver.totalMinutes().doubleValue() / 1440).isCloseTo(1.11, within(0.02));
		assertThat(justOver.billableDays())
				.as("rounding 1.1 days up to 2 is unfair to the customer")
				.isEqualTo(1);

		assertThat(almostOneAndAHalf.totalMinutes().doubleValue() / 1440).isCloseTo(1.40, within(0.02));
		assertThat(almostOneAndAHalf.billableDays())
				.as("a crew will not take another job for half a day")
				.isEqualTo(2);
	}

	@Test
	@DisplayName("a job too small to fill a day is charged one crew day, not its line items")
	void minimumBindsOnASmallJob() {
		PricedQuote quote = engine.price(
				stage1(new BigDecimal("6.00"), RoomInput.declared(RoomType.BATHROOM, WallCondition.GOOD)), book());

		assertThat(quote.billableDays())
				.as("a crew that turns up has spent a day, however small the job")
				.isEqualTo(1);
		assertThat(quote.minimumBinding()).isTrue();
		assertThat(quote.totalCost()).isEqualByComparingTo(quote.minimumCost());
		assertThat(quote.subtotalExVat())
				.isEqualByComparingTo(quote.minimumCost().multiply(new BigDecimal("1.30")));
	}

	@Test
	@DisplayName("§5.8: rounding happens at line total and grand total, and nowhere in between")
	void roundsOnlyAtLineTotalAndGrandTotal() {
		PricedQuote quote = engine.price(workedExample(), book());
		QuoteLine wall = quote.line(ItemCode.WALL_PAINT);

		assertThat(wall.quantity().scale())
				.as("220.83 m² is a rounded reading of 220.8310…; pricing the rounded figure loses money")
				.isGreaterThan(2);
		assertThat(wall.lineTotal().scale()).isEqualTo(2);
		assertThat(quote.total().scale()).isEqualTo(2);
		assertThat(wall.lineTotal())
				.as("HALF_UP on the sum of the unrounded portions")
				.isEqualByComparingTo(wall.labourCost().add(wall.materialCost())
						.setScale(2, java.math.RoundingMode.HALF_UP));
	}

	@Test
	@DisplayName("§5.8: labour and material are taxed at their own rates, not at a blended one")
	void taxesLabourAndMaterialAtTheirOwnRates() {
		PricingInput input = stage1(new BigDecimal("92.00"),
				RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.MINOR));

		PricedQuote labourHeavy = engine.price(input, bookWithVat("0.20", "0.10"));
		PricedQuote materialHeavy = engine.price(input, bookWithVat("0.10", "0.20"));

		assertThat(labourHeavy.vatAmount())
				.as("swapping the two rates must change the VAT, or the portions are being blended")
				.isNotEqualByComparingTo(materialHeavy.vatAmount());

		// Independently: the published lines, marked up by the margin, at each portion's own rate.
		BigDecimal labour = labourHeavy.lines().stream().map(QuoteLine::labourCost)
				.reduce(BigDecimal.ZERO, BigDecimal::add).multiply(new BigDecimal("1.30"));
		BigDecimal material = labourHeavy.lines().stream().map(QuoteLine::materialCost)
				.reduce(BigDecimal.ZERO, BigDecimal::add).multiply(new BigDecimal("1.30"));
		assertThat(labourHeavy.vatAmount()).isCloseTo(
				labour.multiply(new BigDecimal("0.20")).add(material.multiply(new BigDecimal("0.10"))),
				within(CENT));
	}

	// =============================================================================================
	// §5.9 — band width. Widens, never shifts.
	// =============================================================================================

	@ParameterizedTest(name = "UNSURE={0} gross={1} estimated doors={2} → band {3}")
	@CsvSource({
		"false,false,false,0.12", "true,false,false,0.27", "false,true,false,0.17",
		"false,false,true,0.15", "true,true,false,0.32", "true,false,true,0.30",
		"false,true,true,0.20", "true,true,true,0.35"})
	@DisplayName("§5.9: every uncertainty combination adds its own term")
	void everyUncertaintyCombinationAddsItsOwnTerm(
			boolean unsure, boolean gross, boolean estimatedDoors, String expected) {
		PricingInput input = new PricingInput("TEST", new BigDecimal("60.00"), gross,
				List.of(RoomInput.declared(RoomType.LIVING_ROOM,
						unsure ? WallCondition.UNSURE : WallCondition.GOOD)),
				Furnishing.EMPTY, 3, false, estimatedDoors, true, false, PricingSource.STAGE_1);

		assertThat(engine.price(input, book()).bandRatio()).isEqualByComparingTo(expected);
	}

	@Test
	@DisplayName("§5.9: low analysis confidence widens the band by (1 − confidence) × 0.40")
	void lowAnalysisConfidenceWidensTheBand() {
		SurfaceInput unsure = new SurfaceInput("WALL_1", Coating.PAINTED, Tone.LIGHT, FillerBand.LOW,
				false, Moisture.NONE, false, new BigDecimal("0.50"));
		PricedQuote quote = engine.price(
				stage2(new BigDecimal("40.00"),
						RoomInput.analysed(RoomType.LIVING_ROOM, List.of(unsure), 0, 0, 0, 0, false)),
				book());

		assertThat(quote.bandRatio()).isEqualByComparingTo("0.32");
	}

	@Test
	@DisplayName("§5.9: a wider band never moves the midpoint")
	void aWiderBandNeverMovesTheMidpoint() {
		PricedQuote confident = engine.price(
				stage1(new BigDecimal("30.00"), RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.MINOR)), book());
		PricedQuote unsure = engine.price(
				stage1(new BigDecimal("30.00"), RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.UNSURE)), book());

		for (PricedQuote quote : List.of(confident, unsure)) {
			assertThat(quote.bandLow().add(quote.bandHigh()).divide(new BigDecimal("2")))
					.as("nobody opens a wall and finds it better than expected")
					.isCloseTo(quote.total(), within(CENT));
		}
		assertThat(unsure.bandRatio()).isGreaterThan(confident.bandRatio());
	}

	// =============================================================================================
	// fixtures
	// =============================================================================================

	private void assertLine(QuoteLine line, String quantity, String labour, String material, String total) {
		assertThat(line.quantity()).as("%s quantity", line.code())
				.isCloseTo(new BigDecimal(quantity), within(CENT));
		assertThat(line.labourCost()).as("%s labour", line.code())
				.isCloseTo(new BigDecimal(labour), within(CENT));
		assertThat(line.materialCost()).as("%s material", line.code())
				.isCloseTo(new BigDecimal(material), within(CENT));
		assertThat(line.lineTotal()).as("%s line total", line.code())
				.isEqualByComparingTo(new BigDecimal(total));
	}

	private void assertQuantity(PricedQuote quote, ItemCode code, BigDecimal expected) {
		if (expected.signum() == 0) {
			assertThat(quote.hasLine(code)).as("%s should not appear at all", code).isFalse();
		} else {
			assertThat(quote.line(code).quantity()).as("%s quantity", code)
					.isCloseTo(expected, within(CENT));
		}
	}

	/** The minutes of every line but mobilization, recomputed from the published quantities. */
	private BigDecimal minutesOf(PricedQuote quote) {
		return quote.lines().stream()
				.filter(line -> line.code() != ItemCode.MOBILIZATION)
				.map(line -> line.quantity().multiply(book().item(line.code()).labourMinutes()))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, java.math.RoundingMode.HALF_UP);
	}

	private static SurfaceInput clean(String id) {
		return new SurfaceInput(id, Coating.PAINTED, Tone.LIGHT, FillerBand.NONE, false,
				Moisture.NONE, false, new BigDecimal("1.000"));
	}

	private static SurfaceInput dark(String id) {
		return new SurfaceInput(id, Coating.PAINTED, Tone.DARK, FillerBand.NONE, false,
				Moisture.NONE, false, new BigDecimal("1.000"));
	}

	private static SurfaceInput tiled(String id) {
		return new SurfaceInput(id, Coating.TILE, Tone.LIGHT, FillerBand.NONE, false,
				Moisture.NONE, false, new BigDecimal("1.000"));
	}

	private static PricingInput stage1(BigDecimal netArea, RoomInput... rooms) {
		return new PricingInput("TEST", netArea, false, List.of(rooms), Furnishing.EMPTY,
				0, false, false, true, false, PricingSource.STAGE_1);
	}

	private static PricingInput stage2(BigDecimal netArea, RoomInput... rooms) {
		return new PricingInput("TEST", netArea, false, List.of(rooms), Furnishing.EMPTY,
				0, false, false, true, false, PricingSource.STAGE_2);
	}

	private static PricingInput workedExample() {
		List<RoomInput> rooms = List.of(
				RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.MINOR),
				RoomInput.declared(RoomType.MASTER_BEDROOM, WallCondition.MINOR),
				RoomInput.declared(RoomType.BEDROOM, WallCondition.MINOR),
				RoomInput.declared(RoomType.BEDROOM, WallCondition.MINOR),
				RoomInput.declared(RoomType.KITCHEN, WallCondition.MINOR),
				RoomInput.declared(RoomType.BATHROOM, WallCondition.MINOR),
				RoomInput.declared(RoomType.HALLWAY, WallCondition.MINOR));
		return new PricingInput("KADIKOY", new BigDecimal("92.00"), false, rooms,
				Furnishing.FURNISHED, 8, true, false, true, false, PricingSource.STAGE_1);
	}

	private static PricingInput oneRoom(Furnishing furnishing, boolean rush) {
		return new PricingInput("TEST", new BigDecimal("30.00"), false,
				List.of(RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.MINOR)),
				furnishing, 2, false, false, true, rush, PricingSource.STAGE_1);
	}

	private static PricingInput oneRoomWithoutElevator() {
		return new PricingInput("TEST", new BigDecimal("30.00"), false,
				List.of(RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.MINOR)),
				Furnishing.EMPTY, 2, false, false, false, false, PricingSource.STAGE_1);
	}

	private static PricingInput withDoors(int doors, boolean colourChange) {
		return new PricingInput("TEST", new BigDecimal("60.00"), false,
				List.of(RoomInput.declared(RoomType.LIVING_ROOM, WallCondition.GOOD),
						RoomInput.declared(RoomType.BEDROOM, WallCondition.GOOD)),
				Furnishing.EMPTY, doors, colourChange, false, true, false, PricingSource.STAGE_1);
	}

	/** §5.11's seed, plus the 1.05 Kadıköy factor §5.10 prices against. */
	private static PriceBook book() {
		return bookWithVat("0.2000", "0.2000");
	}

	private static PriceBook bookWithVat(String labourRate, String materialRate) {
		Map<ItemCode, PriceBookItem> items = new EnumMap<>(ItemCode.class);
		item(items, ItemCode.WALL_PAINT, "62", "38", "6");
		item(items, ItemCode.CEILING_PAINT, "70", "38", "8");
		item(items, ItemCode.PATCH_FILLING, "50", "15", "12");
		item(items, ItemCode.SKIM_COAT, "100", "42", "22");
		item(items, ItemCode.PRIMER, "20", "15", "3");
		item(items, ItemCode.STAIN_BLOCK_PRIMER, "25", "40", "4");
		item(items, ItemCode.WALLPAPER_STRIPPING, "48", "2", "14");
		item(items, ItemCode.DOOR_PAINT, "350", "150", "55");
		item(items, ItemCode.TRIM_PAINT, "140", "52", "22");
		item(items, ItemCode.RADIATOR_PAINT, "270", "115", "40");
		item(items, ItemCode.DOWNLIGHT_CUTTING, "46", "0", "8");
		item(items, ItemCode.CORNICE_CUTTING, "308", "0", "45");
		item(items, ItemCode.MASKING, "115", "62", "25");
		item(items, ItemCode.MOBILIZATION, "1900", "0", "60");

		Map<ModifierCode, PriceModifier> modifiers = new EnumMap<>(ModifierCode.class);
		modifiers.put(ModifierCode.FURNISHED, new PriceModifier(
				ModifierCode.FURNISHED, new BigDecimal("1.2500"), ModifierTarget.LABOUR, Set.of()));
		modifiers.put(ModifierCode.RUSH, new PriceModifier(
				ModifierCode.RUSH, new BigDecimal("1.2500"), ModifierTarget.LABOUR, Set.of()));
		modifiers.put(ModifierCode.DARK_TO_LIGHT, new PriceModifier(
				ModifierCode.DARK_TO_LIGHT, new BigDecimal("1.5000"), ModifierTarget.BOTH,
				Set.of(ItemCode.WALL_PAINT, ItemCode.DOOR_PAINT)));
		modifiers.put(ModifierCode.NO_ELEVATOR, new PriceModifier(
				ModifierCode.NO_ELEVATOR, new BigDecimal("1.2000"), ModifierTarget.BOTH,
				Set.of(ItemCode.MOBILIZATION)));

		Map<RoomType, RoomTypeConfig> rooms = new EnumMap<>(RoomType.class);
		roomType(rooms, RoomType.LIVING_ROOM, "3.0", "4.1", "1.00");
		roomType(rooms, RoomType.MASTER_BEDROOM, "1.5", "4.1", "1.00");
		roomType(rooms, RoomType.BEDROOM, "1.2", "4.1", "1.00");
		roomType(rooms, RoomType.STUDY, "1.0", "4.1", "1.00");
		roomType(rooms, RoomType.KITCHEN, "1.1", "4.3", "0.65");
		roomType(rooms, RoomType.BATHROOM, "0.5", "4.2", "0.20");
		roomType(rooms, RoomType.HALLWAY, "0.8", "5.5", "1.00");
		roomType(rooms, RoomType.BALCONY, "0.4", "4.3", "1.00");

		return new PriceBook("TEST-5.11",
				new BigDecimal("2.70"), new BigDecimal("0.82"), new BigDecimal("0.12"),
				new BigDecimal("1.90"), new BigDecimal("2.20"),
				3, new BigDecimal("8.00"), new BigDecimal("4500.00"), new BigDecimal("0.25"),
				new BigDecimal("0.30"), new BigDecimal("0.20"),
				new BigDecimal(labourRate), new BigDecimal(materialRate), new BigDecimal("0.12"),
				items, modifiers, rooms,
				Map.of("KADIKOY", new ServiceDistrict("KADIKOY", "Kadıköy", true, new BigDecimal("1.05"))));
	}

	private static void item(Map<ItemCode, PriceBookItem> into, ItemCode code,
			String labour, String material, String minutes) {
		into.put(code, new PriceBookItem(code, new BigDecimal(labour), new BigDecimal(material),
				new BigDecimal(minutes)));
	}

	private static void roomType(Map<RoomType, RoomTypeConfig> into, RoomType type,
			String weight, String perimeter, String paintable) {
		into.put(type, new RoomTypeConfig(type, new BigDecimal(weight), new BigDecimal(perimeter),
				new BigDecimal(paintable)));
	}
}
