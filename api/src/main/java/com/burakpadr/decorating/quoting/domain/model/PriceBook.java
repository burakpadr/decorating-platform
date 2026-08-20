package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One price book version, in full (§4.5, §5.11).
 *
 * <p>Self-contained by construction, as the table is: items, modifiers, room types and district
 * factors all belong to the version. A quote records which version priced it, so a later change to a
 * coefficient cannot retroactively alter a quote already sent (see {@code docs/decisions/0010}).
 *
 * <p>Everything here is data the engine reads and nothing it interprets — no defaults hide in this
 * class. A missing item or room type is a programming error the engine reports rather than papers
 * over, because a line item priced at nothing is money lost with no visible symptom.
 */
public record PriceBook(
		String versionCode,
		BigDecimal ceilingHeightM,
		BigDecimal grossToNetRatio,
		BigDecimal stage1OpeningRatio,
		BigDecimal doorOpeningM2,
		BigDecimal windowOpeningM2,
		int crewSize,
		BigDecimal crewHoursPerDay,
		BigDecimal crewDayCost,
		BigDecimal dayRoundingTolerance,
		BigDecimal marginRatio,
		BigDecimal marginAlertThreshold,
		BigDecimal labourVatRate,
		BigDecimal materialVatRate,
		BigDecimal baseBandRatio,
		Map<ItemCode, PriceBookItem> items,
		Map<ModifierCode, PriceModifier> modifiers,
		Map<RoomType, RoomTypeConfig> roomTypes,
		Map<String, BigDecimal> districtFactors) {

	public PriceBook {
		items = Map.copyOf(items);
		modifiers = Map.copyOf(modifiers);
		roomTypes = Map.copyOf(roomTypes);
		districtFactors = Map.copyOf(districtFactors);
	}

	public PriceBookItem item(ItemCode code) {
		PriceBookItem item = items.get(code);
		if (item == null) {
			throw new IllegalStateException(
					"price book " + versionCode + " has no row for " + code
							+ " — every code in §5.6 is a quantity the engine looks up");
		}
		return item;
	}

	public RoomTypeConfig roomType(RoomType type) {
		RoomTypeConfig config = roomTypes.get(type);
		if (config == null) {
			throw new IllegalStateException(
					"price book " + versionCode + " has no configuration for room type " + type);
		}
		return config;
	}

	/**
	 * The factor for a district. An unknown district is 1.0000 rather than an error: the district
	 * list is per version, and a quote must not fail because a newly served district has no row yet.
	 */
	public BigDecimal districtFactor(String districtCode) {
		return districtFactors.getOrDefault(districtCode, BigDecimal.ONE);
	}
}
