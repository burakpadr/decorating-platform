package com.burakpadr.decorating.quoting.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.quoting.domain.PriceBookFixture;
import com.burakpadr.decorating.quoting.domain.model.ActivationProblem;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookItem;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.RoomTypeConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What has to be true before a price book version may price a customer's home (BOYA-20a).
 *
 * <p>This is ADR 0016's guard, moved. It used to be a schema test reading whatever book was active in
 * the test database; BOYA-72 took the price book out of the migrations, so that test now guards a
 * fixture and nothing else. Here it guards every book — entered by hand, copied, raised, or produced
 * by the setup wizard — at the one moment that matters, which is activation.
 *
 * <p>Pure, like the engine it protects: this is the gate in front of every price the version would go
 * on to produce, so every branch has to be drivable without a database.
 */
class ActivationCheckTest {

	private final ActivationCheck check = new ActivationCheck();

	@Test
	@DisplayName("a book whose item labour matches its own crew rate can be activated")
	void aReconciledBookHasNoProblems() {
		assertThat(check.check(reconciled(PriceBookFixture.seed()))).isEmpty();
	}

	@Test
	@DisplayName("§5.11's published table cannot be activated — every one of its items disagrees")
	void theSpecsOwnSeedIsRefused() {
		// Not a contrived case: this is the table the project shipped with, and the defect BOYA-22a
		// found in production. 62 TL for six person-minutes implies 4,960 TL a person-day against a book
		// that says 1,500. The fixture keeps those figures on purpose — PricingEngineTest holds §5.10's
		// worked example to them — which makes it the honest input for this test.
		assertThat(check.check(PriceBookFixture.seed()))
				.hasSize(ItemCode.values().length)
				.allSatisfy(problem -> assertThat(problem.kind())
						.isEqualTo(ActivationProblem.Kind.LABOUR_DOES_NOT_RECONCILE));
	}

	@Test
	@DisplayName("one item out by a single kuruş is still refused, and the item is named")
	void aSingleCentIsEnough() {
		PriceBook book = reconciled(PriceBookFixture.seed());
		PriceBookItem wall = book.item(ItemCode.WALL_PAINT);
		PriceBook drifted = withItem(book, new PriceBookItem(wall.code(), wall.unit(),
				wall.labourCost().add(new BigDecimal("0.01")), wall.materialCost(), wall.labourMinutes()));

		assertThat(check.check(drifted))
				.singleElement()
				.satisfies(problem -> {
					assertThat(problem.kind()).isEqualTo(ActivationProblem.Kind.LABOUR_DOES_NOT_RECONCILE);
					assertThat(problem.subject())
							.as("naming it, because 'this version does not reconcile' sends somebody "
									+ "through fourteen rows to find out which")
							.isEqualTo(ItemCode.WALL_PAINT.name());
				});
	}

	@Test
	@DisplayName("a missing item is refused too: the engine throws on it at pricing time")
	void aMissingItemIsRefused() {
		PriceBook book = reconciled(PriceBookFixture.seed());
		Map<ItemCode, PriceBookItem> items = new EnumMap<>(book.items());
		items.remove(ItemCode.MOBILIZATION);

		assertThat(check.check(withItems(book, items)))
				.singleElement()
				.satisfies(problem -> {
					assertThat(problem.kind()).isEqualTo(ActivationProblem.Kind.MISSING_ITEM);
					assertThat(problem.subject()).isEqualTo(ItemCode.MOBILIZATION.name());
				});
	}

	@Test
	@DisplayName("a missing room type is refused for the same reason")
	void aMissingRoomTypeIsRefused() {
		PriceBook book = reconciled(PriceBookFixture.seed());
		Map<RoomType, RoomTypeConfig> types = new EnumMap<>(book.roomTypes());
		types.remove(RoomType.HALLWAY);

		assertThat(check.check(withRoomTypes(book, types)))
				.singleElement()
				.satisfies(problem -> {
					assertThat(problem.kind()).isEqualTo(ActivationProblem.Kind.MISSING_ROOM_TYPE);
					assertThat(problem.subject()).isEqualTo(RoomType.HALLWAY.name());
				});
	}

	// =============================================================================================
	// Helpers. `reconciled` is the rule written the other way round — from minutes to money — which is
	// what the panel and the wizard both do (ADR 0016).
	// =============================================================================================

	private static PriceBook reconciled(PriceBook book) {
		// Multiply first, divide last — the arithmetic the database performs. Deriving a per-minute rate
		// and multiplying by it disagrees by a kuruş wherever the result lands on exactly half of one.
		BigDecimal minutesInACrewDay = BigDecimal.valueOf(book.crewSize())
				.multiply(book.crewHoursPerDay()).multiply(new BigDecimal("60"));
		Map<ItemCode, PriceBookItem> items = new EnumMap<>(ItemCode.class);
		book.items().forEach((code, item) -> items.put(code, new PriceBookItem(
				item.code(), item.unit(),
				item.labourMinutes().multiply(book.crewDayCost())
						.divide(minutesInACrewDay, 2, RoundingMode.HALF_UP),
				item.materialCost(), item.labourMinutes())));
		return withItems(book, items);
	}

	private static PriceBook withItem(PriceBook book, PriceBookItem item) {
		Map<ItemCode, PriceBookItem> items = new EnumMap<>(book.items());
		items.put(item.code(), item);
		return withItems(book, items);
	}

	private static PriceBook withItems(PriceBook book, Map<ItemCode, PriceBookItem> items) {
		return rebuild(book, items, book.roomTypes());
	}

	private static PriceBook withRoomTypes(PriceBook book, Map<RoomType, RoomTypeConfig> types) {
		return rebuild(book, book.items(), types);
	}

	private static PriceBook rebuild(PriceBook b, Map<ItemCode, PriceBookItem> items,
			Map<RoomType, RoomTypeConfig> types) {
		return new PriceBook(b.versionCode(), b.ceilingHeightM(), b.grossToNetRatio(),
				b.stage1OpeningRatio(), b.doorOpeningM2(), b.windowOpeningM2(), b.crewSize(),
				b.crewHoursPerDay(), b.crewDayCost(), b.dayRoundingTolerance(), b.marginRatio(),
				b.marginAlertThreshold(), b.surveyAmountFactor(), b.averageJobValue(), b.labourVatRate(),
				b.materialVatRate(), b.baseBandRatio(), items, b.modifiers(), types, b.districts());
	}
}
