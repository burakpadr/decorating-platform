package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
		Map<String, ServiceDistrict> districts) {

	/**
	 * Turkish collation, because a Latin sort puts Ç after Z and Ş after Z — so Çekmeköy and Şişli would
	 * land at the bottom of a list a customer is scanning for their own district.
	 */
	private static final Collator TURKISH = Collator.getInstance(new Locale("tr", "TR"));

	public PriceBook {
		items = Map.copyOf(items);
		modifiers = Map.copyOf(modifiers);
		roomTypes = Map.copyOf(roomTypes);
		districts = Map.copyOf(districts);
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
	 * The factor for a district. An unknown district is 1.0000 rather than an error: the district list
	 * is per version, and a quote must not fail because a newly served district has no row yet.
	 *
	 * <p>{@code active} is not consulted. Whether a district is served is decided by the first question
	 * of stage 1, long before pricing; a quote already taken for a district since switched off must
	 * still price the way it was quoted.
	 */
	/**
	 * §5.10's step 10. An unlisted district prices at 1.0000 rather than failing, which is deliberate and
	 * is <b>not</b> a service check: the operator's tool prices hypothetical addresses, and
	 * {@link #serves} is what a customer-facing path has to ask first.
	 */
	public BigDecimal districtFactor(String districtCode) {
		ServiceDistrict district = districts.get(districtCode);
		return district == null ? BigDecimal.ONE : district.districtFactor();
	}

	/**
	 * Whether this is an area the business works in (workflow §7 decision 1).
	 *
	 * <p>Switched off, not deleted: a district that closes keeps its factor and its history, and the
	 * quotes priced while it was open stay readable. So "served" is the flag, not the presence of a row.
	 */
	public boolean serves(String districtCode) {
		ServiceDistrict district = districts.get(districtCode);
		return district != null && district.active();
	}

	/** The districts a customer may choose, by display name — the order the list is read in. */
	public List<ServiceDistrict> servedDistricts() {
		return districts.values().stream()
				.filter(ServiceDistrict::active)
				.sorted(Comparator.comparing(ServiceDistrict::displayName, TURKISH))
				.toList();
	}
}
