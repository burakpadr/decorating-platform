package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierCode;
import com.burakpadr.decorating.quoting.domain.model.ModifierTarget;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.PriceBookStructure;
import com.burakpadr.decorating.quoting.domain.model.PriceModifier;
import com.burakpadr.decorating.quoting.domain.model.PriceUnit;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.RoomTypeConfig;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookStructures;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the shipped price book structure off the classpath (BOYA-70, decision 0006's arrangement).
 *
 * <p>Here rather than in a package of its own because the file is the price book's own shape, and
 * this is where the price book's storage lives — the same reason the vision prompt sits beside the
 * adapter that sends it.
 *
 * <p>The version is a constant rather than a property, for the reason {@code ClasspathConsentNotices}
 * gives: a property could name a file the running artefact does not contain, and a price book that
 * cannot say what it was built from is one nobody can explain later. Bumping it happens in the commit
 * that adds the next file.
 *
 * <p>Parsed once at construction. A malformed file is a startup failure and should be — the
 * alternative is discovering it when somebody is halfway through setting up.
 */
@Component
class ClasspathPriceBookStructure implements PriceBookStructures {

	private static final String CURRENT = "v1";

	private final PriceBookStructure structure;

	ClasspathPriceBookStructure() {
		this.structure = read(CURRENT);
	}

	@Override
	public PriceBookStructure current() {
		return structure;
	}

	private static PriceBookStructure read(String version) {
		String path = "price-book/structure-" + version + ".json";
		JsonNode root;
		try {
			root = JsonMapper.builder().build()
					.readTree(new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(path + " is missing from the artefact", e);
		}

		JsonNode c = root.get("coefficients");
		return new PriceBookStructure(
				root.get("version").asString(),
				decimal(c, "ceilingHeightM"),
				decimal(c, "grossToNetRatio"),
				decimal(c, "stage1OpeningRatio"),
				decimal(c, "doorOpeningM2"),
				decimal(c, "windowOpeningM2"),
				c.get("crewSize").asInt(),
				decimal(c, "crewHoursPerDay"),
				decimal(c, "dayRoundingTolerance"),
				decimal(c, "surveyAmountFactor"),
				decimal(c, "baseBandRatio"),
				items(root.get("items")),
				modifiers(root.get("modifiers")),
				roomTypes(root.get("roomTypes")));
	}

	private static Map<ItemCode, PriceBookStructure.StructureItem> items(JsonNode array) {
		Map<ItemCode, PriceBookStructure.StructureItem> items = new EnumMap<>(ItemCode.class);
		for (JsonNode node : array) {
			ItemCode code = ItemCode.valueOf(node.get("code").asString());
			items.put(code, new PriceBookStructure.StructureItem(code,
					PriceUnit.valueOf(node.get("unit").asString()), decimal(node, "labourMinutes")));
		}
		return items;
	}

	private static Map<ModifierCode, PriceModifier> modifiers(JsonNode array) {
		Map<ModifierCode, PriceModifier> modifiers = new EnumMap<>(ModifierCode.class);
		for (JsonNode node : array) {
			ModifierCode code = ModifierCode.valueOf(node.get("code").asString());
			Set<ItemCode> scope = new LinkedHashSet<>();
			node.get("scopeItems").forEach(item -> scope.add(ItemCode.valueOf(item.asString())));
			modifiers.put(code, new PriceModifier(code, decimal(node, "factor"),
					ModifierTarget.valueOf(node.get("appliesTo").asString()), scope));
		}
		return modifiers;
	}

	private static Map<RoomType, RoomTypeConfig> roomTypes(JsonNode array) {
		Map<RoomType, RoomTypeConfig> types = new EnumMap<>(RoomType.class);
		for (JsonNode node : array) {
			RoomType type = RoomType.valueOf(node.get("roomType").asString());
			List<PhotoRole> photos = new ArrayList<>();
			node.get("requiredPhotos").forEach(photo -> photos.add(PhotoRole.valueOf(photo.asString())));
			types.put(type, new RoomTypeConfig(type, decimal(node, "areaWeight"),
					decimal(node, "perimeterFactor"), decimal(node, "paintableRatio"), photos));
		}
		return types;
	}

	/**
	 * Read through the text rather than as a double: these figures reach {@code numeric} columns and
	 * the engine multiplies every square metre by them, so binary floating point has no business in
	 * the path.
	 */
	private static BigDecimal decimal(JsonNode parent, String field) {
		JsonNode node = parent.get(field);
		if (node == null) {
			throw new IllegalStateException("the price book structure has no " + field);
		}
		return new BigDecimal(node.asString());
	}
}
