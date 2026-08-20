package com.burakpadr.decorating.quoting.domain.service;

import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookItem;
import com.burakpadr.decorating.quoting.domain.model.PriceModifier;
import com.burakpadr.decorating.quoting.domain.model.PricedQuote;
import com.burakpadr.decorating.quoting.domain.model.PricingInput;
import com.burakpadr.decorating.quoting.domain.model.QuoteLine;
import com.burakpadr.decorating.quoting.domain.model.RoomInput;
import com.burakpadr.decorating.quoting.domain.model.RoomTypeConfig;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns observations and declared measurements into money (§5). Pure: no I/O, no framework, no
 * clock, nothing but the JDK and this module's domain — enforced by
 * {@code ArchitectureRulesTest#pricingEngineIsPure}.
 *
 * <p>The 13 steps of §5.2 run in the order written there. The order is not stylistic: modifiers
 * compound, so moving one changes the result. Two of those steps are easy to misread and the worked
 * example in §5.10 settles both:
 *
 * <ul>
 *   <li><b>Mobilization is added at step 9</b>, after the labour modifiers of step 7. So a furnished
 *       home does not pay 25% more to have the van loaded, and mobilization's minutes are not part of
 *       the duration — §5.10's 3,293 minutes only reconciles when they are left out.
 *   <li><b>DARK_TO_LIGHT scales minutes as well as money.</b> It means a third coat, so it targets
 *       {@code BOTH} and is one of the labour modifiers §5.8 multiplies minutes by. §5.10's door line
 *       (8 × 500 × 1.50) and its minute total only agree with each other this way.
 * </ul>
 *
 * <p>Rounding follows §5.8: at line total and grand total, {@code HALF_UP}, nowhere in between.
 * Quantities are therefore reported unrounded — 220.83 m² of wall is priced as 220.8310…, and it is
 * the presentation layer's business to show two decimals. Rounding a quantity before multiplying it
 * by a unit cost is the one place where a fraction of a lira per line becomes real money.
 */
public final class PricingEngine {

	/** Enough precision that division and square roots never dominate the rounding at the end. */
	private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

	private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");

	/** §5.6: MAJOR damage puts a skim coat on a quarter of the wall area. */
	private static final BigDecimal MAJOR_SKIM_SHARE = new BigDecimal("0.25");

	/** §5.9's band terms. */
	private static final BigDecimal UNSURE_WIDENING = new BigDecimal("0.15");
	private static final BigDecimal GROSS_AREA_WIDENING = new BigDecimal("0.05");
	private static final BigDecimal ESTIMATED_DOORS_WIDENING = new BigDecimal("0.03");

	public PricedQuote price(PricingInput input, PriceBook book) {
		Map<ItemCode, BigDecimal> quantities = quantities(input, book);

		List<QuoteLine> lines = new ArrayList<>();
		Money items = Money.ZERO;
		BigDecimal minutes = BigDecimal.ZERO;

		for (Map.Entry<ItemCode, BigDecimal> entry : quantities.entrySet()) {
			ItemCode code = entry.getKey();
			if (code == ItemCode.MOBILIZATION) {
				continue;                                    // step 9, below
			}
			PriceBookItem item = book.item(code);
			BigDecimal quantity = entry.getValue();

			// Step 6 — item-level modifiers, then step 7 — labour modifiers. Both are collected as a
			// factor per portion so that the multiplication happens once and in the stated order.
			BigDecimal itemFactorLabour = itemModifierFactor(input, book, code, true);
			BigDecimal itemFactorMaterial = itemModifierFactor(input, book, code, false);
			BigDecimal labourFactor = labourModifierFactor(input, book);

			BigDecimal labour = quantity.multiply(item.labourCost())
					.multiply(itemFactorLabour).multiply(labourFactor);
			BigDecimal material = quantity.multiply(item.materialCost()).multiply(itemFactorMaterial);

			// §5.8: minutes carry the labour modifiers, and an item modifier that touches labour is
			// one of them — more coats is more time.
			minutes = minutes.add(quantity.multiply(item.labourMinutes())
					.multiply(itemFactorLabour).multiply(labourFactor));

			lines.add(line(code, quantity, labour, material));
			items = items.add(labour, material);
		}

		// Step 9 — mobilization and floor access. Outside steps 6–8 by §5.2's ordering: no labour
		// modifier reaches it and its minutes are not part of the crew's day.
		BigDecimal mobilizationQuantity = quantities.get(ItemCode.MOBILIZATION);
		Money withMobilization = items;
		if (mobilizationQuantity != null) {
			PriceBookItem item = book.item(ItemCode.MOBILIZATION);
			BigDecimal factorLabour = itemModifierFactor(input, book, ItemCode.MOBILIZATION, true);
			BigDecimal factorMaterial = itemModifierFactor(input, book, ItemCode.MOBILIZATION, false);
			BigDecimal labour = mobilizationQuantity.multiply(item.labourCost()).multiply(factorLabour);
			BigDecimal material =
					mobilizationQuantity.multiply(item.materialCost()).multiply(factorMaterial);
			lines.add(line(ItemCode.MOBILIZATION, mobilizationQuantity, labour, material));
			withMobilization = items.add(labour, material);
		}

		// Step 10 — the district factor, on the whole subtotal.
		BigDecimal district = book.districtFactor(input.districtCode());
		Money afterDistrict = withMobilization.scale(district);

		// Step 11 — the minimum.
		BigDecimal personHours = minutes.divide(MINUTES_PER_HOUR, MC);
		BigDecimal crewCapacity = new BigDecimal(book.crewSize()).multiply(book.crewHoursPerDay());
		BigDecimal days = personHours.divide(crewCapacity, MC);
		int billableDays = billableDays(days, book.dayRoundingTolerance(), lines.isEmpty());
		BigDecimal minimumCost = new BigDecimal(billableDays).multiply(book.crewDayCost());

		Money cost = afterDistrict;
		boolean minimumBinding = minimumCost.compareTo(afterDistrict.total()) > 0;
		if (minimumBinding) {
			// The minimum is a floor on labour — it exists because a crew turned up for a day — so the
			// uplift lands on the labour portion and is taxed as labour at step 13.
			cost = new Money(
					afterDistrict.labour().add(minimumCost.subtract(afterDistrict.total())),
					afterDistrict.material());
		}

		// Step 12 — margin. Step 13 — VAT, at each portion's own rate.
		BigDecimal marginMultiplier = BigDecimal.ONE.add(book.marginRatio());
		Money sale = cost.scale(marginMultiplier);
		BigDecimal vat = sale.labour().multiply(book.labourVatRate())
				.add(sale.material().multiply(book.materialVatRate()));
		BigDecimal subtotalExVat = sale.total();
		BigDecimal total = subtotalExVat.add(vat);

		BigDecimal bandRatio = bandRatio(input, book);
		return new PricedQuote(
				book.versionCode(),
				lines,
				money(minutes),
				billableDays,
				money(minimumCost),
				minimumBinding,
				money(cost.total()),
				money(subtotalExVat),
				money(vat),
				money(total),
				bandRatio,
				money(total.multiply(BigDecimal.ONE.subtract(bandRatio))),
				money(total.multiply(BigDecimal.ONE.add(bandRatio))));
	}

	/** Steps 1–5: room areas, wall areas, deductions, and the quantity each item code is priced on. */
	private Map<ItemCode, BigDecimal> quantities(PricingInput input, PriceBook book) {
		BigDecimal sumWeights = input.rooms().stream()
				.map(room -> book.roomType(room.type()).areaWeight())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (sumWeights.signum() == 0) {
			throw new IllegalArgumentException("a quote needs at least one room");
		}

		BigDecimal wallNet = BigDecimal.ZERO;
		BigDecimal ceilingArea = BigDecimal.ZERO;
		BigDecimal filler = BigDecimal.ZERO;
		BigDecimal skim = BigDecimal.ZERO;

		for (RoomInput room : input.rooms()) {
			RoomTypeConfig config = book.roomType(room.type());

			// Step 1 — the net area is shared out by weight, not by room count.
			BigDecimal area = input.netArea().multiply(config.areaWeight()).divide(sumWeights, MC);
			// Step 2 — a perimeter derived from the area, at the type's own aspect ratio.
			BigDecimal wallGross =
					config.perimeterFactor().multiply(area.sqrt(MC)).multiply(book.ceilingHeightM());
			// Steps 3–4 — what is painted, less the openings. Stage 1 has no counted openings, so it
			// uses the flat ratio; stage 2 counts doors and windows instead (§5.5).
			BigDecimal afterCoating = wallGross.multiply(config.paintableRatio());
			BigDecimal net = afterCoating.multiply(BigDecimal.ONE.subtract(book.stage1OpeningRatio()));

			wallNet = wallNet.add(net);
			ceilingArea = ceilingArea.add(area);
			filler = filler.add(net.multiply(fillerRatio(room.declaredCondition())));
			if (room.declaredCondition() == WallCondition.MAJOR) {
				skim = skim.add(net.multiply(MAJOR_SKIM_SHARE));
			}
		}

		// Step 5, in §5.6's order. A zero quantity is left out rather than priced at nothing: a line
		// for work nobody will do is a line the operator has to explain.
		Map<ItemCode, BigDecimal> quantities = new EnumMap<>(ItemCode.class);
		put(quantities, ItemCode.WALL_PAINT, wallNet);
		put(quantities, ItemCode.CEILING_PAINT, ceilingArea);
		put(quantities, ItemCode.PATCH_FILLING, filler);
		put(quantities, ItemCode.SKIM_COAT, skim);
		put(quantities, ItemCode.DOOR_PAINT, new BigDecimal(input.doorCount()));
		put(quantities, ItemCode.MASKING, new BigDecimal(input.rooms().size()));
		put(quantities, ItemCode.MOBILIZATION, BigDecimal.ONE);
		return quantities;
	}

	/** §5.6's stage 1 table: a declared condition becomes a filler ratio on every surface. */
	private BigDecimal fillerRatio(WallCondition condition) {
		return switch (condition) {
			case GOOD -> BigDecimal.ZERO;
			case MINOR -> new BigDecimal("0.15");
			case MAJOR -> new BigDecimal("0.40");
			// Not knowing is not damage: UNSURE prices near MINOR and widens the band instead (§5.9).
			case UNSURE -> new BigDecimal("0.20");
		};
	}

	/**
	 * Step 6 — the item-scoped modifiers.
	 *
	 * <p>{@code DARK_TO_LIGHT} covers walls and doors, but stage 1 carries no surface tone, so only a
	 * declared door colour change can trigger it here. Walls become eligible when stage 2 reports a
	 * dark tone.
	 */
	private BigDecimal itemModifierFactor(
			PricingInput input, PriceBook book, ItemCode code, boolean labour) {
		BigDecimal factor = BigDecimal.ONE;

		if (code == ItemCode.DOOR_PAINT && input.doorColourChange()) {
			factor = factor.multiply(factorOf(book, ModifierCode.DARK_TO_LIGHT, code, labour));
		}
		if (!input.hasElevator()) {
			factor = factor.multiply(factorOf(book, ModifierCode.NO_ELEVATOR, code, labour));
		}
		return factor;
	}

	/**
	 * Step 7 — the labour modifiers.
	 *
	 * <p>PARTIAL furnishing is half the <em>delta</em>, not half the factor: 1.25 becomes 1.125, not
	 * 0.625.
	 */
	private BigDecimal labourModifierFactor(PricingInput input, PriceBook book) {
		BigDecimal factor = BigDecimal.ONE;

		PriceModifier furnished = book.modifiers().get(ModifierCode.FURNISHED);
		if (furnished != null && input.furnishing() != Furnishing.EMPTY) {
			BigDecimal delta = furnished.factor().subtract(BigDecimal.ONE);
			if (input.furnishing() == Furnishing.PARTIAL) {
				delta = delta.divide(new BigDecimal("2"), MC);
			}
			factor = factor.multiply(BigDecimal.ONE.add(delta));
		}

		PriceModifier rush = book.modifiers().get(ModifierCode.RUSH);
		if (rush != null && input.rush()) {
			factor = factor.multiply(rush.factor());
		}
		return factor;
	}

	private BigDecimal factorOf(PriceBook book, ModifierCode code, ItemCode item, boolean labour) {
		PriceModifier modifier = book.modifiers().get(code);
		if (modifier == null || !modifier.covers(item)) {
			return BigDecimal.ONE;
		}
		boolean applies = labour ? modifier.affectsLabour() : modifier.affectsMaterial();
		return applies ? modifier.factor() : BigDecimal.ONE;
	}

	/**
	 * §5.8's {@code ceil(days − tolerance)}, with a floor of one day.
	 *
	 * <p>The tolerance is there so 1.1 days is not billed as 2 while 1.4 still is — a crew will not
	 * take another job for half a day. Applied literally, though, it also turns a two-hour bathroom
	 * into zero billable days, which makes {@code minimumCost} zero and leaves the minimum unable to
	 * bind on exactly the small jobs it exists for (workflow §12, "küçük işlerde taban"). A crew that
	 * turns up has spent a day. See {@code docs/decisions/0013}.
	 */
	private int billableDays(BigDecimal days, BigDecimal tolerance, boolean nothingToDo) {
		if (nothingToDo) {
			return 0;
		}
		int rounded = days.subtract(tolerance).setScale(0, RoundingMode.CEILING).intValue();
		return Math.max(1, rounded);
	}

	/** §5.9. Uncertainty widens the band; it never moves the midpoint. */
	private BigDecimal bandRatio(PricingInput input, PriceBook book) {
		BigDecimal ratio = book.baseBandRatio();
		if (input.rooms().stream().anyMatch(r -> r.declaredCondition() == WallCondition.UNSURE)) {
			ratio = ratio.add(UNSURE_WIDENING);
		}
		if (input.areaWasGross()) {
			ratio = ratio.add(GROSS_AREA_WIDENING);
		}
		if (input.doorCountEstimated()) {
			ratio = ratio.add(ESTIMATED_DOORS_WIDENING);
		}
		return ratio;
	}

	private QuoteLine line(ItemCode code, BigDecimal quantity, BigDecimal labour, BigDecimal material) {
		return new QuoteLine(code, quantity, labour, material, money(labour.add(material)));
	}

	private static void put(Map<ItemCode, BigDecimal> quantities, ItemCode code, BigDecimal quantity) {
		if (quantity.signum() > 0) {
			quantities.put(code, quantity);
		}
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	/** The labour/material split, carried to step 13 where the two are taxed at different rates. */
	private record Money(BigDecimal labour, BigDecimal material) {

		private static final Money ZERO = new Money(BigDecimal.ZERO, BigDecimal.ZERO);

		Money add(BigDecimal moreLabour, BigDecimal moreMaterial) {
			return new Money(labour.add(moreLabour), material.add(moreMaterial));
		}

		Money scale(BigDecimal factor) {
			return new Money(labour.multiply(factor), material.multiply(factor));
		}

		BigDecimal total() {
			return labour.add(material);
		}
	}
}
