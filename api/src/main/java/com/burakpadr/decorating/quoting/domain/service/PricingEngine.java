package com.burakpadr.decorating.quoting.domain.service;

import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookItem;
import com.burakpadr.decorating.quoting.domain.model.PriceModifier;
import com.burakpadr.decorating.quoting.domain.model.PricedQuote;
import com.burakpadr.decorating.quoting.domain.model.PricingInput;
import com.burakpadr.decorating.quoting.domain.model.PricingSource;
import com.burakpadr.decorating.quoting.domain.model.QuoteLine;
import com.burakpadr.decorating.quoting.domain.model.QuotePortion;
import com.burakpadr.decorating.quoting.domain.model.RoomInput;
import com.burakpadr.decorating.quoting.domain.model.RoomTypeConfig;
import com.burakpadr.decorating.quoting.domain.model.SurfaceInput;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns observations and declared measurements into money (§5). Pure: no I/O, no framework, no clock,
 * nothing but the JDK and this module's domain — enforced by
 * {@code ArchitectureRulesTest#pricingEngineIsPure}.
 *
 * <p>The 13 steps of §5.2 run in the order written there. The order is not stylistic: modifiers
 * compound, so moving one changes the result. Two of those steps are easy to misread and §5.10's
 * worked example settles both:
 *
 * <ul>
 *   <li><b>Mobilization is added at step 9</b>, after the labour modifiers of step 7. So a furnished
 *       home does not pay 25% more to have the van loaded, and mobilization's minutes are not part of
 *       the duration — §5.10's 3,293 minutes only reconciles when they are left out.
 *   <li><b>DARK_TO_LIGHT scales minutes as well as money.</b> It means a third coat, so it targets
 *       {@code BOTH} and is one of the labour modifiers §5.8 multiplies minutes by.
 * </ul>
 *
 * <p>Rounding follows §5.8: at line total and grand total, {@code HALF_UP}, nowhere in between.
 * Quantities are therefore reported unrounded — 220.83 m² of wall is priced as 220.8310…, and it is
 * the presentation layer's business to show two decimals. Rounding a quantity before multiplying it by
 * a unit cost is where a fraction of a lira per line quietly becomes real money.
 *
 * <p>Stage 1 and stage 2 differ in exactly the two places §5.5 and §5.9 name: how openings are
 * deducted, and how wide the band is. Everything else — allocation, modifiers, duration, margin, VAT —
 * is one path, so the two stages cannot drift apart. See {@code docs/decisions/0014} for what §5.5 and
 * §5.6 leave to the reader.
 */
public final class PricingEngine {

	/** Enough precision that division and square roots never dominate the rounding at the end. */
	private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

	private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");
	private static final BigDecimal TWO = new BigDecimal("2");

	/** §5.6: MAJOR damage puts a skim coat on a quarter of the wall area. */
	private static final BigDecimal MAJOR_SKIM_SHARE = new BigDecimal("0.25");

	/** §5.5: however many openings stage 2 counts, a wall keeps 60% of its area. */
	private static final BigDecimal OPENING_DEDUCTION_FLOOR = new BigDecimal("0.60");

	/** §5.9's band terms. */
	private static final BigDecimal UNSURE_WIDENING = new BigDecimal("0.15");
	private static final BigDecimal GROSS_AREA_WIDENING = new BigDecimal("0.05");
	private static final BigDecimal ESTIMATED_DOORS_WIDENING = new BigDecimal("0.03");
	private static final BigDecimal CONFIDENCE_WIDENING = new BigDecimal("0.40");

	public PricedQuote price(PricingInput input, PriceBook book) {
		Measured measured = measure(input, book);
		Map<ItemCode, BigDecimal> quantities = measured.quantities();

		List<QuoteLine> lines = new ArrayList<>();
		Money items = Money.ZERO;
		BigDecimal minutes = BigDecimal.ZERO;
		BigDecimal labourFactor = labourModifierFactor(input, book);

		for (Map.Entry<ItemCode, BigDecimal> entry : quantities.entrySet()) {
			ItemCode code = entry.getKey();
			if (code == ItemCode.MOBILIZATION) {
				continue;                                    // step 9, below
			}
			PriceBookItem item = book.item(code);
			BigDecimal quantity = entry.getValue();

			// Step 6 — item-level modifiers. Step 7 — labour modifiers. Collected as one factor per
			// portion so the multiplication happens once, in the stated order.
			BigDecimal itemLabour = itemModifierFactor(input, book, measured, code, true);
			BigDecimal itemMaterial = itemModifierFactor(input, book, measured, code, false);

			BigDecimal labour = quantity.multiply(item.labourCost()).multiply(itemLabour).multiply(labourFactor);
			BigDecimal material = quantity.multiply(item.materialCost()).multiply(itemMaterial);

			// §5.8: minutes carry the labour modifiers, and an item modifier that touches labour is one
			// of them — more coats is more time.
			minutes = minutes.add(
					quantity.multiply(item.labourMinutes()).multiply(itemLabour).multiply(labourFactor));

			lines.add(line(code, item, quantity, labour, material));
			items = items.add(labour, material);
		}

		// Step 9 — mobilization and floor access, outside steps 6–8 by §5.2's ordering.
		Money withMobilization = items;
		BigDecimal mobilizationQuantity = quantities.get(ItemCode.MOBILIZATION);
		if (mobilizationQuantity != null) {
			PriceBookItem item = book.item(ItemCode.MOBILIZATION);
			BigDecimal labour = mobilizationQuantity.multiply(item.labourCost())
					.multiply(itemModifierFactor(input, book, measured, ItemCode.MOBILIZATION, true));
			BigDecimal material = mobilizationQuantity.multiply(item.materialCost())
					.multiply(itemModifierFactor(input, book, measured, ItemCode.MOBILIZATION, false));
			lines.add(line(ItemCode.MOBILIZATION, item, mobilizationQuantity, labour, material));
			withMobilization = items.add(labour, material);
		}

		// Step 10 — the district factor, on the whole subtotal.
		Money afterDistrict = withMobilization.scale(book.districtFactor(input.districtCode()));

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
		Money sale = cost.scale(BigDecimal.ONE.add(book.marginRatio()));
		BigDecimal labourVat = sale.labour().multiply(book.labourVatRate());
		BigDecimal materialVat = sale.material().multiply(book.materialVatRate());
		BigDecimal vat = labourVat.add(materialVat);
		BigDecimal subtotalExVat = sale.total();
		BigDecimal total = subtotalExVat.add(vat);

		// The two halves, as figures rather than as an internal accumulator. Each is rounded from its own
		// unrounded chain, so each is the figure that half is actually worth. Deriving one by subtracting
		// the other from the whole would make them add up to the cent, but at a worse price: the material
		// half would then absorb labour's rounding and move when only labour changed — a material cost
		// that shifts by a kuruş because the home is furnished is a figure nobody can explain. See
		// QuotePortion.
		QuotePortion labourPortion = new QuotePortion(
				money(cost.labour()),
				money(sale.labour()),
				money(labourVat),
				money(sale.labour().add(labourVat)));
		QuotePortion materialPortion = new QuotePortion(
				money(cost.material()),
				money(sale.material()),
				money(materialVat),
				money(sale.material().add(materialVat)));

		BigDecimal bandRatio = bandRatio(input, book, measured);
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
				labourPortion,
				materialPortion,
				bandRatio,
				money(total.multiply(BigDecimal.ONE.subtract(bandRatio))),
				money(total.multiply(BigDecimal.ONE.add(bandRatio))));
	}

	// -----------------------------------------------------------------------------------------------
	// Steps 1–5
	// -----------------------------------------------------------------------------------------------

	/**
	 * Room areas, wall areas, deductions, and the quantity each item code is priced on — plus the two
	 * figures later steps need that are only knowable here: how much of the wall area is dark, and how
	 * confident the analysis was.
	 */
	private Measured measure(PricingInput input, PriceBook book) {
		BigDecimal sumWeights = input.rooms().stream()
				.map(room -> book.roomType(room.type()).areaWeight())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		if (sumWeights.signum() == 0) {
			throw new IllegalArgumentException("a quote needs at least one room");
		}

		Totals totals = new Totals();
		for (RoomInput room : input.rooms()) {
			RoomTypeConfig config = book.roomType(room.type());

			// Step 1 — the net area is shared out by weight, not by room count.
			BigDecimal area = input.netArea().multiply(config.areaWeight()).divide(sumWeights, MC);
			// Step 2 — a perimeter derived from the area, at the type's own aspect ratio.
			BigDecimal wallGross =
					config.perimeterFactor().multiply(area.sqrt(MC)).multiply(book.ceilingHeightM());

			totals.ceilingArea = totals.ceilingArea.add(area);
			if (input.source() == PricingSource.STAGE_1) {
				measureDeclared(room, config, book, wallGross, totals);
			} else {
				measureAnalysed(room, book, wallGross, totals);
			}

			totals.trim = totals.trim.add(new BigDecimal(room.windowCount()));
			totals.radiators = totals.radiators.add(new BigDecimal(room.radiatorCount()));
			totals.downlights = totals.downlights.add(new BigDecimal(room.downlightCount()));
			if (room.cornice()) {
				totals.cornices = totals.cornices.add(BigDecimal.ONE);
			}
		}

		// Step 5, in §5.6's order. A zero quantity is left out rather than priced at nothing: a line for
		// work nobody will do is a line the operator has to explain.
		Map<ItemCode, BigDecimal> quantities = new EnumMap<>(ItemCode.class);
		put(quantities, ItemCode.WALL_PAINT, totals.wallNet);
		put(quantities, ItemCode.CEILING_PAINT, totals.ceilingArea);
		put(quantities, ItemCode.PATCH_FILLING, totals.filler);
		put(quantities, ItemCode.SKIM_COAT, totals.skim);
		put(quantities, ItemCode.PRIMER, totals.primer);
		put(quantities, ItemCode.STAIN_BLOCK_PRIMER, totals.stainBlock);
		put(quantities, ItemCode.WALLPAPER_STRIPPING, totals.wallpaper);
		put(quantities, ItemCode.DOOR_PAINT, new BigDecimal(input.doorCount()));
		put(quantities, ItemCode.TRIM_PAINT, totals.trim);
		put(quantities, ItemCode.RADIATOR_PAINT, totals.radiators);
		put(quantities, ItemCode.DOWNLIGHT_CUTTING, totals.downlights);
		put(quantities, ItemCode.CORNICE_CUTTING, totals.cornices);
		put(quantities, ItemCode.MASKING, new BigDecimal(input.rooms().size()));
		put(quantities, ItemCode.MOBILIZATION, BigDecimal.ONE);

		BigDecimal darkShare = totals.wallNet.signum() == 0
				? BigDecimal.ZERO
				: totals.darkWall.divide(totals.wallNet, MC);
		BigDecimal confidence = totals.confidenceWeight.signum() == 0
				? null
				: totals.confidenceSum.divide(totals.confidenceWeight, MC);
		return new Measured(quantities, darkShare, confidence);
	}

	/**
	 * Stage 1 — steps 3–4 with the flat opening ratio, and §5.6's declared-condition table.
	 *
	 * <p>The declaration applies to every surface of the room, which is the point: a customer cannot
	 * report wall by wall, so one answer stands in for all of them.
	 */
	private void measureDeclared(
			RoomInput room, RoomTypeConfig config, PriceBook book, BigDecimal wallGross, Totals totals) {
		BigDecimal afterCoating = wallGross.multiply(config.paintableRatio());
		BigDecimal net = afterCoating.multiply(BigDecimal.ONE.subtract(book.stage1OpeningRatio()));

		totals.wallNet = totals.wallNet.add(net);
		totals.filler = totals.filler.add(net.multiply(fillerRatio(room.declaredCondition())));
		if (room.declaredCondition() == WallCondition.MAJOR) {
			BigDecimal skimmed = net.multiply(MAJOR_SKIM_SHARE);
			totals.skim = totals.skim.add(skimmed);
			// §5.6: primer follows the skim coat. Skimmed plaster drinks paint; priming it is not
			// optional, so a skim coat that arrives without primer is an underquote.
			totals.primer = totals.primer.add(skimmed);
		}
	}

	/**
	 * Stage 2 — steps 3–4 from the findings, and §5.6's per-surface quantity sources.
	 *
	 * <p>Two things §5.5 leaves to the reader, both recorded in {@code docs/decisions/0014}: a room's
	 * wall area is split equally across its analysed surfaces, and a surface whose coating is not
	 * {@code PAINTED} is dropped before the split rather than discounted after it.
	 */
	private void measureAnalysed(
			RoomInput room, PriceBook book, BigDecimal wallGross, Totals totals) {
		List<SurfaceInput> surfaces = room.surfaces();
		if (surfaces.isEmpty()) {
			throw new IllegalArgumentException(
					"a stage 2 room needs at least one analysed surface: " + room.type());
		}
		List<SurfaceInput> painted = surfaces.stream().filter(s -> s.coating().isPaintable()).toList();

		// Confidence covers every surface the model reported, painted or not: a tile wall it was unsure
		// about is a tile wall it may have misread.
		for (SurfaceInput surface : surfaces) {
			totals.confidenceSum = totals.confidenceSum.add(surface.confidence());
			totals.confidenceWeight = totals.confidenceWeight.add(BigDecimal.ONE);
		}
		if (painted.isEmpty()) {
			return;                       // a fully tiled room: nothing to paint, nothing to price
		}

		BigDecimal afterCoating = wallGross
				.multiply(new BigDecimal(painted.size()))
				.divide(new BigDecimal(surfaces.size()), MC);
		BigDecimal openings = book.doorOpeningM2().multiply(new BigDecimal(room.doorCount()))
				.add(book.windowOpeningM2().multiply(new BigDecimal(room.windowCount())));
		BigDecimal net = afterCoating.subtract(openings);
		BigDecimal floor = afterCoating.multiply(OPENING_DEDUCTION_FLOOR);
		if (net.compareTo(floor) < 0) {
			// A sanity guard, not a discount: a room reported with more doors than it can hold would
			// otherwise deduct its walls away.
			net = floor;
		}

		BigDecimal perSurface = net.divide(new BigDecimal(painted.size()), MC);
		totals.wallNet = totals.wallNet.add(net);

		for (SurfaceInput surface : painted) {
			totals.filler = totals.filler.add(perSurface.multiply(surface.fillerBand().ratio()));
			if (surface.skimCoatRequired()) {
				totals.skim = totals.skim.add(perSurface);
			}
			if (surface.skimCoatRequired() || surface.tone() == Tone.DARK) {
				totals.primer = totals.primer.add(perSurface);
			}
			if (surface.moisture().needsStainBlock()) {
				totals.stainBlock = totals.stainBlock.add(perSurface);
			}
			if (surface.wallpaper()) {
				totals.wallpaper = totals.wallpaper.add(perSurface);
			}
			if (surface.tone() == Tone.DARK) {
				totals.darkWall = totals.darkWall.add(perSurface);
			}
		}
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

	// -----------------------------------------------------------------------------------------------
	// Steps 6–7
	// -----------------------------------------------------------------------------------------------

	/**
	 * Step 6 — the item-scoped modifiers.
	 *
	 * <p>{@code DARK_TO_LIGHT} covers walls and doors. Doors take the full factor when the customer
	 * declared a colour change. Walls take it in proportion to the dark share of the wall area, because
	 * one dark accent wall is not a reason to repaint the whole flat three times
	 * ({@code docs/decisions/0014}).
	 */
	private BigDecimal itemModifierFactor(
			PricingInput input, PriceBook book, Measured measured, ItemCode code, boolean labour) {
		BigDecimal factor = BigDecimal.ONE;

		if (code == ItemCode.DOOR_PAINT && input.doorColourChange()) {
			factor = factor.multiply(factorOf(book, ModifierCode.DARK_TO_LIGHT, code, labour));
		}
		if (code == ItemCode.WALL_PAINT && measured.darkWallShare().signum() > 0) {
			BigDecimal full = factorOf(book, ModifierCode.DARK_TO_LIGHT, code, labour);
			factor = factor.multiply(
					BigDecimal.ONE.add(full.subtract(BigDecimal.ONE).multiply(measured.darkWallShare())));
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
				delta = delta.divide(TWO, MC);
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

	// -----------------------------------------------------------------------------------------------
	// Steps 11 and the band
	// -----------------------------------------------------------------------------------------------

	/**
	 * §5.8's {@code ceil(days − tolerance)}, with a floor of one day.
	 *
	 * <p>The tolerance is there so 1.1 days is not billed as 2 while 1.4 still is — a crew will not
	 * take another job for half a day. Applied literally it also turns a two-hour bathroom into zero
	 * billable days, which makes {@code minimumCost} zero and leaves the minimum unable to bind on
	 * exactly the small jobs it exists for. A crew that turns up has spent a day. See
	 * {@code docs/decisions/0013}.
	 */
	private int billableDays(BigDecimal days, BigDecimal tolerance, boolean nothingToDo) {
		if (nothingToDo) {
			return 0;
		}
		return Math.max(1, days.subtract(tolerance).setScale(0, RoundingMode.CEILING).intValue());
	}

	/** §5.9. Uncertainty widens the band; it never moves the midpoint. */
	private BigDecimal bandRatio(PricingInput input, PriceBook book, Measured measured) {
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
		if (measured.avgSurfaceConfidence() != null) {
			ratio = ratio.add(BigDecimal.ONE.subtract(measured.avgSurfaceConfidence())
					.multiply(CONFIDENCE_WIDENING));
		}
		return ratio;
	}

	private QuoteLine line(ItemCode code, PriceBookItem item, BigDecimal quantity, BigDecimal labour,
			BigDecimal material) {
		return new QuoteLine(code, item.unit(), quantity, labour, material, money(labour.add(material)));
	}

	private static void put(Map<ItemCode, BigDecimal> quantities, ItemCode code, BigDecimal quantity) {
		if (quantity.signum() > 0) {
			quantities.put(code, quantity);
		}
	}

	private static BigDecimal money(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	/** What steps 1–5 produced, including the two figures only they can know. */
	private record Measured(
			Map<ItemCode, BigDecimal> quantities,
			BigDecimal darkWallShare,
			BigDecimal avgSurfaceConfidence) {}

	/** Mutable accumulator for the room loop. Private, single-threaded, never escapes. */
	private static final class Totals {
		private BigDecimal wallNet = BigDecimal.ZERO;
		private BigDecimal ceilingArea = BigDecimal.ZERO;
		private BigDecimal filler = BigDecimal.ZERO;
		private BigDecimal skim = BigDecimal.ZERO;
		private BigDecimal primer = BigDecimal.ZERO;
		private BigDecimal stainBlock = BigDecimal.ZERO;
		private BigDecimal wallpaper = BigDecimal.ZERO;
		private BigDecimal trim = BigDecimal.ZERO;
		private BigDecimal radiators = BigDecimal.ZERO;
		private BigDecimal downlights = BigDecimal.ZERO;
		private BigDecimal cornices = BigDecimal.ZERO;
		private BigDecimal darkWall = BigDecimal.ZERO;
		private BigDecimal confidenceSum = BigDecimal.ZERO;
		private BigDecimal confidenceWeight = BigDecimal.ZERO;
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
