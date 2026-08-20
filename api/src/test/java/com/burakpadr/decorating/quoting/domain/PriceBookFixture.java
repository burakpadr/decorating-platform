package com.burakpadr.decorating.quoting.domain;

import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierTarget;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookItem;
import com.burakpadr.decorating.quoting.domain.model.PriceModifier;
import com.burakpadr.decorating.quoting.domain.model.PriceUnit;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.RoomTypeConfig;
import com.burakpadr.decorating.quoting.domain.model.ServiceDistrict;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * §5.11's seed as a value, shared by every pure-domain test.
 *
 * <p>One copy on purpose: two transcriptions of the same table drift, and a fixture that disagrees
 * with the migration is a suite that passes while the product is wrong. The migration is asserted
 * against §5.11 separately, by {@code PriceBookIntegrityTest}.
 *
 * <p>Kadıköy carries the 1.05 factor §5.10's worked example prices against; the seeded migration has
 * every district at 1.0000, so a test that wants the example's arithmetic needs it set here.
 */
public final class PriceBookFixture {

	private PriceBookFixture() {}

	public static PriceBook seed() {
		return seedWithVat("0.2000", "0.2000");
	}

	public static PriceBook seedWithVat(String labourRate, String materialRate) {
		return new PriceBook("TEST-5.11",
				new BigDecimal("2.70"), new BigDecimal("0.82"), new BigDecimal("0.12"),
				new BigDecimal("1.90"), new BigDecimal("2.20"),
				3, new BigDecimal("8.00"), new BigDecimal("4500.00"), new BigDecimal("0.25"),
				new BigDecimal("0.30"), new BigDecimal("0.20"),
				new BigDecimal(labourRate), new BigDecimal(materialRate), new BigDecimal("0.12"),
				items(), modifiers(), roomTypes(),
				Map.of("KADIKOY", new ServiceDistrict("KADIKOY", "Kadıköy", true, new BigDecimal("1.05"))));
	}

	private static Map<ItemCode, PriceBookItem> items() {
		Map<ItemCode, PriceBookItem> items = new EnumMap<>(ItemCode.class);
		item(items, ItemCode.WALL_PAINT, PriceUnit.SQM, "62", "38", "6");
		item(items, ItemCode.CEILING_PAINT, PriceUnit.SQM, "70", "38", "8");
		item(items, ItemCode.PATCH_FILLING, PriceUnit.SQM, "50", "15", "12");
		item(items, ItemCode.SKIM_COAT, PriceUnit.SQM, "100", "42", "22");
		item(items, ItemCode.PRIMER, PriceUnit.SQM, "20", "15", "3");
		item(items, ItemCode.STAIN_BLOCK_PRIMER, PriceUnit.SQM, "25", "40", "4");
		item(items, ItemCode.WALLPAPER_STRIPPING, PriceUnit.SQM, "48", "2", "14");
		item(items, ItemCode.DOOR_PAINT, PriceUnit.UNIT, "350", "150", "55");
		item(items, ItemCode.TRIM_PAINT, PriceUnit.UNIT, "140", "52", "22");
		item(items, ItemCode.RADIATOR_PAINT, PriceUnit.UNIT, "270", "115", "40");
		item(items, ItemCode.DOWNLIGHT_CUTTING, PriceUnit.UNIT, "46", "0", "8");
		item(items, ItemCode.CORNICE_CUTTING, PriceUnit.ROOM, "308", "0", "45");
		item(items, ItemCode.MASKING, PriceUnit.ROOM, "115", "62", "25");
		item(items, ItemCode.MOBILIZATION, PriceUnit.LUMP_SUM, "1900", "0", "60");
		return items;
	}

	private static Map<ModifierCode, PriceModifier> modifiers() {
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
		return modifiers;
	}

	/** §5.3's table, including the frames §2.4 asks for in each kind of room. */
	private static Map<RoomType, RoomTypeConfig> roomTypes() {
		Map<RoomType, RoomTypeConfig> rooms = new EnumMap<>(RoomType.class);
		roomType(rooms, RoomType.LIVING_ROOM, "3.0", "4.1", "1.00", FOUR_WALLS);
		roomType(rooms, RoomType.MASTER_BEDROOM, "1.5", "4.1", "1.00", FOUR_WALLS);
		roomType(rooms, RoomType.BEDROOM, "1.2", "4.1", "1.00", FOUR_WALLS);
		roomType(rooms, RoomType.STUDY, "1.0", "4.1", "1.00", FOUR_WALLS);
		roomType(rooms, RoomType.KITCHEN, "1.1", "4.3", "0.65", TWO_CORNERS);
		roomType(rooms, RoomType.BATHROOM, "0.5", "4.2", "0.20", ONE_GENERAL);
		roomType(rooms, RoomType.HALLWAY, "0.8", "5.5", "1.00", TWO_CORNERS);
		roomType(rooms, RoomType.BALCONY, "0.4", "4.3", "1.00", ONE_GENERAL);
		return rooms;
	}

	private static final List<PhotoRole> FOUR_WALLS = List.of(
			PhotoRole.WALL_1, PhotoRole.WALL_2, PhotoRole.WALL_3, PhotoRole.WALL_4, PhotoRole.CEILING);
	private static final List<PhotoRole> TWO_CORNERS =
			List.of(PhotoRole.WALL_1, PhotoRole.WALL_2, PhotoRole.CEILING);
	private static final List<PhotoRole> ONE_GENERAL = List.of(PhotoRole.WALL_1, PhotoRole.CEILING);

	private static void item(Map<ItemCode, PriceBookItem> into, ItemCode code, PriceUnit unit,
			String labour, String material, String minutes) {
		into.put(code, new PriceBookItem(code, unit, new BigDecimal(labour), new BigDecimal(material),
				new BigDecimal(minutes)));
	}

	private static void roomType(Map<RoomType, RoomTypeConfig> into, RoomType type,
			String weight, String perimeter, String paintable, List<PhotoRole> photos) {
		into.put(type, new RoomTypeConfig(type, new BigDecimal(weight), new BigDecimal(perimeter),
				new BigDecimal(paintable), photos));
	}
}
