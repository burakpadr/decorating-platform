package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierTarget;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookItem;
import com.burakpadr.decorating.quoting.domain.model.PriceModifier;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.RoomTypeConfig;
import com.burakpadr.decorating.quoting.domain.model.ServiceDistrict;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rows to domain, explicitly. No reflection, no framework, one direction.
 *
 * <p><b>An unknown code is skipped, not fatal.</b> A price book may carry a row this build has no
 * enum constant for — a code added by a later version, or one being retired — and the engine only
 * ever looks up the codes §5.6 names. Failing the load would take the whole quoting flow down over a
 * row nothing reads. The opposite direction is the dangerous one and is guarded elsewhere: a code the
 * engine *does* need missing from the book is what {@code PriceBookIntegrityTest} fails the build for,
 * and what {@code PriceBook#item} throws on rather than pricing at zero.
 */
final class PriceBookMapper {

	/**
	 * Item codes inside a {@code scope_items} array. Read with a regex rather than a JSON library
	 * because the column holds one shape and only ever one — a flat array of code strings — and a
	 * dependency on Jackson here would put a serialization framework between the database and the
	 * pricing domain.
	 */
	private static final Pattern QUOTED = Pattern.compile("\"([A-Z_]+)\"");

	private PriceBookMapper() {}

	static PriceBook toDomain(
			PriceBookEntity book,
			List<PriceBookItemEntity> items,
			List<PriceModifierEntity> modifiers,
			List<RoomTypeConfigEntity> roomTypes,
			List<ServiceDistrictEntity> districts) {

		Map<ItemCode, PriceBookItem> mappedItems = new EnumMap<>(ItemCode.class);
		for (PriceBookItemEntity item : items) {
			enumOf(ItemCode.class, item.getCode()).ifPresent(code -> mappedItems.put(code,
					new PriceBookItem(code, item.getLabourCost(), item.getMaterialCost(),
							item.getLabourMinutes())));
		}

		Map<ModifierCode, PriceModifier> mappedModifiers = new EnumMap<>(ModifierCode.class);
		for (PriceModifierEntity modifier : modifiers) {
			enumOf(ModifierCode.class, modifier.getCode()).ifPresent(code -> mappedModifiers.put(code,
					new PriceModifier(code, modifier.getFactor(),
							ModifierTarget.valueOf(modifier.getAppliesTo()),
							scopeOf(modifier.getScopeItems()))));
		}

		Map<RoomType, RoomTypeConfig> mappedRoomTypes = new EnumMap<>(RoomType.class);
		for (RoomTypeConfigEntity roomType : roomTypes) {
			enumOf(RoomType.class, roomType.getRoomType()).ifPresent(type -> mappedRoomTypes.put(type,
					new RoomTypeConfig(type, roomType.getAreaWeight(), roomType.getPerimeterFactor(),
							roomType.getPaintableRatio())));
		}

		Map<String, ServiceDistrict> mappedDistricts = new LinkedHashMap<>();
		for (ServiceDistrictEntity district : districts) {
			mappedDistricts.put(district.getDistrictCode(), new ServiceDistrict(
					district.getDistrictCode(), district.getDisplayName(), district.isActive(),
					district.getDistrictFactor()));
		}

		return new PriceBook(
				book.getVersionCode(),
				book.getCeilingHeightM(),
				book.getGrossToNetRatio(),
				book.getStage1OpeningRatio(),
				book.getDoorOpeningM2(),
				book.getWindowOpeningM2(),
				book.getCrewSize(),
				book.getCrewHoursPerDay(),
				book.getCrewDayCost(),
				book.getDayRoundingTolerance(),
				book.getMarginRatio(),
				book.getMarginAlertThreshold(),
				book.getLabourVatRate(),
				book.getMaterialVatRate(),
				book.getBaseBandRatio(),
				mappedItems,
				mappedModifiers,
				mappedRoomTypes,
				mappedDistricts);
	}

	/** A null or empty {@code scope_items} means every item, matching the column's own convention. */
	private static Set<ItemCode> scopeOf(String json) {
		if (json == null || json.isBlank()) {
			return Set.of();
		}
		Set<ItemCode> scope = EnumSet.noneOf(ItemCode.class);
		Matcher matcher = QUOTED.matcher(json);
		while (matcher.find()) {
			enumOf(ItemCode.class, matcher.group(1)).ifPresent(scope::add);
		}
		return scope;
	}

	private static <E extends Enum<E>> java.util.Optional<E> enumOf(Class<E> type, String value) {
		try {
			return java.util.Optional.of(Enum.valueOf(type, value));
		} catch (IllegalArgumentException unknownToThisBuild) {
			return java.util.Optional.empty();
		}
	}
}
