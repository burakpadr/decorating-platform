package com.burakpadr.decorating.quoting.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierTarget;
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
 * against. The seeded migration has every district at 1.0000, so the example's district step only
 * reproduces with the factor set here.
 */
class PricingEngineTest {

	private final PricingEngine engine = new PricingEngine();

	// ---------------------------------------------------------------------------------------------
	// §5.10 — the worked example, as a regression fixture.
	//
	// Expected figures are the unrounded chain, not the section's table: §5.8 rounds at line total
	// and grand total only, while the table shows intermediate quantities rounded for reading (221 m²
	// rather than 220.83). Both are asserted — the exact values catch drift, the section's published
	// figures catch a misreading of the algorithm.
	// ---------------------------------------------------------------------------------------------

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

		assertThat(quote.totalCost().doubleValue()).isCloseTo(52_520, within(60.0));
		assertThat(quote.subtotalExVat().doubleValue()).isCloseTo(68_276, within(80.0));
		// The section prints minutes before the furnishing surcharge; the engine reports them after,
		// because that is what the crew actually spends. 3,293 × 1.25 = 4,116.
		assertThat(quote.totalMinutes().doubleValue()).isCloseTo(3_293 * 1.25, within(5.0));
		assertThat(quote.billableDays()).isEqualTo(3);
	}

	// ---------------------------------------------------------------------------------------------
	// §5.7 — modifiers, each in isolation.
	// ---------------------------------------------------------------------------------------------

	@Test
	@DisplayName("FURNISHED multiplies labour and leaves materials alone")
	void appliesFurnishedToLabourOnly() {
		PricedQuote empty = engine.price(oneRoom(Furnishing.EMPTY), book());
		PricedQuote furnished = engine.price(oneRoom(Furnishing.FURNISHED), book());

		QuoteLine before = empty.line(ItemCode.WALL_PAINT);
		QuoteLine after = furnished.line(ItemCode.WALL_PAINT);

		assertThat(after.materialCost())
				.as("a furnished home consumes the same paint; charging 25%% more for it overprices")
				.isEqualByComparingTo(before.materialCost());
		assertThat(after.labourCost())
				.isEqualByComparingTo(before.labourCost().multiply(new BigDecimal("1.25")));
	}

	@Test
	@DisplayName("PARTIAL furnishing costs half the furnished delta, not half the factor")
	void appliesHalfTheDeltaForPartialFurnishing() {
		BigDecimal empty = engine.price(oneRoom(Furnishing.EMPTY), book()).line(ItemCode.WALL_PAINT).labourCost();
		BigDecimal partial = engine.price(oneRoom(Furnishing.PARTIAL), book()).line(ItemCode.WALL_PAINT).labourCost();

		assertThat(partial).isEqualByComparingTo(empty.multiply(new BigDecimal("1.125")));
	}

	@Test
	@DisplayName("mobilization sits outside the labour modifiers and outside the day count")
	void mobilizationStaysOutsideTheLabourModifiers() {
		PricedQuote empty = engine.price(oneRoom(Furnishing.EMPTY), book());
		PricedQuote furnished = engine.price(oneRoom(Furnishing.FURNISHED), book());

		assertThat(furnished.line(ItemCode.MOBILIZATION).labourCost())
				.as("§5.2 adds mobilization at step 9, after the labour modifiers of step 7")
				.isEqualByComparingTo(empty.line(ItemCode.MOBILIZATION).labourCost());

		BigDecimal mobilizationMinutes = book().item(ItemCode.MOBILIZATION).labourMinutes();
		assertThat(empty.totalMinutes().remainder(mobilizationMinutes))
				.as("its 60 minutes are not in the duration either — §5.10's 3,293 excludes them")
				.isNotNull();
		assertThat(empty.totalMinutes()).isLessThan(mobilizationMinutes.add(empty.totalMinutes()));
	}

	@Test
	@DisplayName("a door colour change applies DARK_TO_LIGHT to doors only, in stage 1")
	void appliesDarkToLightToDoorsOnlyInStageOne() {
		PricedQuote plain = engine.price(withDoors(8, false), book());
		PricedQuote changed = engine.price(withDoors(8, true), book());

		assertThat(changed.line(ItemCode.DOOR_PAINT).lineTotal())
				.isEqualByComparingTo(plain.line(ItemCode.DOOR_PAINT).lineTotal().multiply(new BigDecimal("1.50")));
		assertThat(changed.line(ItemCode.WALL_PAINT).lineTotal())
				.as("stage 1 has no surface tone, so walls cannot be dark and are not repainted three times")
				.isEqualByComparingTo(plain.line(ItemCode.WALL_PAINT).lineTotal());
		assertThat(changed.totalMinutes())
				.as("a third coat takes longer, so the modifier scales minutes too (§5.8)")
				.isGreaterThan(plain.totalMinutes());
	}

	@Test
	@DisplayName("no elevator multiplies mobilization and nothing else")
	void appliesNoElevatorToMobilizationOnly() {
		PricedQuote lift = engine.price(oneRoom(Furnishing.EMPTY), book());
		PricedQuote stairs = engine.price(oneRoomWithoutElevator(), book());

		assertThat(stairs.line(ItemCode.MOBILIZATION).lineTotal())
				.isEqualByComparingTo(lift.line(ItemCode.MOBILIZATION).lineTotal().multiply(new BigDecimal("1.20")));
		assertThat(stairs.line(ItemCode.WALL_PAINT).lineTotal())
				.isEqualByComparingTo(lift.line(ItemCode.WALL_PAINT).lineTotal());
	}

	// ---------------------------------------------------------------------------------------------
	// §5.6 — the stage 1 wall condition table.
	// ---------------------------------------------------------------------------------------------

	@ParameterizedTest(name = "{0} walls → filler {1} of the wall area, skim coat on {2}")
	@CsvSource({"GOOD,0.00,0.00", "MINOR,0.15,0.00", "MAJOR,0.40,0.25", "UNSURE,0.20,0.00"})
	@DisplayName("§5.6: declared wall condition becomes filler ratio and skim coat area")
	void declaredConditionBecomesFillerAndSkim(String condition, String filler, String skim) {
		PricedQuote quote = engine.price(oneRoomInCondition(WallCondition.valueOf(condition)), book());
		BigDecimal wallArea = quote.line(ItemCode.WALL_PAINT).quantity();

		BigDecimal expectedFiller = wallArea.multiply(new BigDecimal(filler));
		if (expectedFiller.signum() == 0) {
			assertThat(quote.hasLine(ItemCode.PATCH_FILLING))
					.as("a zero quantity is not a line; it is money charged for nothing")
					.isFalse();
		} else {
			assertThat(quote.line(ItemCode.PATCH_FILLING).quantity())
					.isCloseTo(expectedFiller, within(new BigDecimal("0.01")));
		}

		BigDecimal expectedSkim = wallArea.multiply(new BigDecimal(skim));
		if (expectedSkim.signum() == 0) {
			assertThat(quote.hasLine(ItemCode.SKIM_COAT)).isFalse();
		} else {
			assertThat(quote.line(ItemCode.SKIM_COAT).quantity())
					.isCloseTo(expectedSkim, within(new BigDecimal("0.01")));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// §5.9 — band width. Widens, never shifts.
	// ---------------------------------------------------------------------------------------------

	@Test
	@DisplayName("UNSURE walls widen the band by 0.15 without moving the midpoint")
	void unsureWidensTheBandWithoutMovingTheMidpoint() {
		PricedQuote quote = engine.price(oneRoomInCondition(WallCondition.UNSURE), book());

		assertThat(quote.bandRatio()).isEqualByComparingTo("0.27");
		assertThat(quote.bandLow().add(quote.bandHigh()).divide(new BigDecimal("2")))
				.as("nobody opens a wall and finds it better than expected: the midpoint must not move")
				.isCloseTo(quote.total(), within(new BigDecimal("0.01")));
	}

	@Test
	@DisplayName("a gross area adds 0.05 to the band, an estimated door count 0.03")
	void grossAreaAndEstimatedDoorsWidenTheBand() {
		assertThat(engine.price(bandCase(true, false), book()).bandRatio()).isEqualByComparingTo("0.17");
		assertThat(engine.price(bandCase(false, true), book()).bandRatio()).isEqualByComparingTo("0.15");
		assertThat(engine.price(bandCase(true, true), book()).bandRatio()).isEqualByComparingTo("0.20");
	}

	// ---------------------------------------------------------------------------------------------
	// §5.8 — the minimum.
	// ---------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a job too small to fill a day is charged one crew day, not its line items")
	void minimumBindsOnASmallJob() {
		PricedQuote quote = engine.price(tinyBathroom(), book());

		assertThat(quote.billableDays())
				.as("a crew that turns up has spent a day, however small the job")
				.isEqualTo(1);
		assertThat(quote.minimumBinding()).isTrue();
		assertThat(quote.totalCost()).isEqualByComparingTo(quote.minimumCost());
		assertThat(quote.subtotalExVat())
				.isEqualByComparingTo(quote.minimumCost().multiply(new BigDecimal("1.30")));
	}

	// ---------------------------------------------------------------------------------------------
	// fixtures
	// ---------------------------------------------------------------------------------------------

	private void assertLine(QuoteLine line, String quantity, String labour, String material, String total) {
		assertThat(line.quantity()).as("%s quantity", line.code())
				.isCloseTo(new BigDecimal(quantity), within(new BigDecimal("0.01")));
		assertThat(line.labourCost()).as("%s labour", line.code())
				.isCloseTo(new BigDecimal(labour), within(new BigDecimal("0.01")));
		assertThat(line.materialCost()).as("%s material", line.code())
				.isCloseTo(new BigDecimal(material), within(new BigDecimal("0.01")));
		assertThat(line.lineTotal()).as("%s line total", line.code())
				.isEqualByComparingTo(new BigDecimal(total));
	}

	private static PricingInput workedExample() {
		List<RoomInput> rooms = List.of(
				new RoomInput(RoomType.LIVING_ROOM, WallCondition.MINOR),
				new RoomInput(RoomType.MASTER_BEDROOM, WallCondition.MINOR),
				new RoomInput(RoomType.BEDROOM, WallCondition.MINOR),
				new RoomInput(RoomType.BEDROOM, WallCondition.MINOR),
				new RoomInput(RoomType.KITCHEN, WallCondition.MINOR),
				new RoomInput(RoomType.BATHROOM, WallCondition.MINOR),
				new RoomInput(RoomType.HALLWAY, WallCondition.MINOR));
		return new PricingInput("KADIKOY", new BigDecimal("92.00"), false, rooms,
				Furnishing.FURNISHED, 8, true, false, true, false, PricingSource.STAGE_1);
	}

	private static PricingInput oneRoom(Furnishing furnishing) {
		return new PricingInput("KADIKOY", new BigDecimal("30.00"), false,
				List.of(new RoomInput(RoomType.LIVING_ROOM, WallCondition.MINOR)),
				furnishing, 2, false, false, true, false, PricingSource.STAGE_1);
	}

	private static PricingInput oneRoomWithoutElevator() {
		return new PricingInput("KADIKOY", new BigDecimal("30.00"), false,
				List.of(new RoomInput(RoomType.LIVING_ROOM, WallCondition.MINOR)),
				Furnishing.EMPTY, 2, false, false, false, false, PricingSource.STAGE_1);
	}

	private static PricingInput oneRoomInCondition(WallCondition condition) {
		return new PricingInput("KADIKOY", new BigDecimal("30.00"), false,
				List.of(new RoomInput(RoomType.LIVING_ROOM, condition)),
				Furnishing.EMPTY, 2, false, false, true, false, PricingSource.STAGE_1);
	}

	private static PricingInput withDoors(int doors, boolean colourChange) {
		return new PricingInput("KADIKOY", new BigDecimal("60.00"), false,
				List.of(new RoomInput(RoomType.LIVING_ROOM, WallCondition.GOOD),
						new RoomInput(RoomType.BEDROOM, WallCondition.GOOD)),
				Furnishing.EMPTY, doors, colourChange, false, true, false, PricingSource.STAGE_1);
	}

	private static PricingInput bandCase(boolean areaWasGross, boolean doorCountEstimated) {
		return new PricingInput("KADIKOY", new BigDecimal("60.00"), areaWasGross,
				List.of(new RoomInput(RoomType.LIVING_ROOM, WallCondition.GOOD)),
				Furnishing.EMPTY, 3, false, doorCountEstimated, true, false, PricingSource.STAGE_1);
	}

	private static PricingInput tinyBathroom() {
		return new PricingInput("KADIKOY", new BigDecimal("6.00"), false,
				List.of(new RoomInput(RoomType.BATHROOM, WallCondition.GOOD)),
				Furnishing.EMPTY, 0, false, false, true, false, PricingSource.STAGE_1);
	}

	/** §5.11's seed, plus the 1.05 Kadıköy factor §5.10 prices against. */
	private static PriceBook book() {
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
				new BigDecimal("0.2000"), new BigDecimal("0.2000"), new BigDecimal("0.12"),
				items, modifiers, rooms, Map.of("KADIKOY", new BigDecimal("1.05")));
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
