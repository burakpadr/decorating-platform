package com.burakpadr.decorating.quoting.adapter.out.vision;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * A model that answers without looking (BOYA-47).
 *
 * <p>The fake sits at {@link VisionModel} rather than at {@code VisionAnalysisPort}, which is a
 * deliberate choice and the more useful one. A fake port would skip everything this adapter does —
 * the versioned prompt, the schema check, the retry, the mapping — so the code that runs in the suite
 * would be the code that does not run in production. A fake model returns JSON and the whole real path
 * runs over it, which also means the fake cannot drift away from the schema without a test noticing.
 *
 * <p>Deterministic per room, seeded on the object path of its first frame. The same room analysed
 * twice produces the same findings, and different rooms produce different ones, so a local run has a
 * spread — including the risky findings, because a fake that can only produce sound walls leaves
 * every branch of the confidence evaluator (BOYA-51) unexercised by hand.
 *
 * <p>It says what it is. {@code model_version} is {@code fake}, so a {@code room_analysis} row that
 * came from nothing is legible as one for as long as the row exists — the audit trail §4.4 asks for,
 * answering the one question that matters about a stored finding.
 */
@Component
@ConditionalOnProperty(name = "decorating.vision.provider", havingValue = "fake")
class FakeVisionModel implements VisionModel {

	/** Never a version anybody could mistake for a provider's. */
	static final String VERSION = "fake";

	private static final Logger log = LoggerFactory.getLogger(FakeVisionModel.class);

	private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

	private static final String[] COATING = {"PAINTED", "PAINTED", "PAINTED", "TILE"};
	private static final String[] TONE = {"LIGHT", "MEDIUM", "DARK"};
	private static final String[] BAND = {"NONE", "LOW", "MEDIUM", "HIGH", "FULL"};
	private static final String[] CRACK = {"NONE", "NONE", "HAIRLINE", "VISIBLE", "STRUCTURAL"};
	private static final String[] MOISTURE = {"NONE", "NONE", "NONE", "STAIN", "ACTIVE"};
	private static final String[] FURNISHING = {"EMPTY", "PARTIAL", "FURNISHED"};
	private static final String[] WALLED_ROOMS = {"LIVING_ROOM", "MASTER_BEDROOM", "BEDROOM", "STUDY"};
	private static final String[] CORNER_ROOMS = {"KITCHEN", "BATHROOM", "HALLWAY", "BALCONY"};

	private static final List<String> NOTES = List.of(
			"sol duvarda priz hizasında ince çatlak",
			"pencere kenarında eski su lekesi, kuru",
			"tavan kenarında kartonpiyer boyunca boya dökülmesi",
			"duvar kağıdı sökülmesi gerekiyor");

	FakeVisionModel() {
		log.warn("decorating.vision.provider=fake — room analyses are invented, not observed. "
				+ "Every room_analysis row this instance writes carries model_version={}", VERSION);
	}

	@Override
	public VisionCompletion complete(VisionPrompt prompt) {
		long seed = seedOf(prompt);
		List<String> walls = prompt.images().stream()
				.map(VisionImage::label)
				.filter(label -> label.startsWith("WALL_"))
				.toList();

		ObjectNode response = NODES.objectNode();
		response.put("roomType", walls.size() >= 4
				? pick(WALLED_ROOMS, seed, 1)
				: walls.isEmpty() ? pick(CORNER_ROOMS, seed, 1) : pick(WALLED_ROOMS, seed, 1));

		ArrayNode surfaces = response.putArray("surfaces");
		if (walls.isEmpty()) {
			// A corner-shot room: one surface for the walls as a whole (§4.4).
			surfaces.add(surface("ROOM_GENERAL", seed, 0));
		}
		else {
			for (int wall = 0; wall < walls.size(); wall++) {
				surfaces.add(surface(walls.get(wall), seed, wall));
			}
		}

		ObjectNode ceiling = response.putObject("ceiling");
		ceiling.put("cornice", seed % 2 == 0);
		ceiling.put("downlightCount", (int) Math.floorMod(seed, 7));
		ceiling.put("staining", pick(MOISTURE, seed, 11));
		ceiling.put("fillerRatio", pick(BAND, seed, 12));
		ceiling.put("confidence", confidence(seed, 13));

		response.put("furnishing", pick(FURNISHING, seed, 21));
		response.put("doorCount", 1 + (int) Math.floorMod(seed, 3));
		response.put("windowCount", 1 + (int) Math.floorMod(seed / 3, 3));
		response.put("radiatorCount", (int) Math.floorMod(seed / 7, 3));
		response.put("confidence", confidence(seed, 22));
		response.putArray("unusablePhotos");
		response.putArray("notes").add(NOTES.get((int) Math.floorMod(seed, NOTES.size())));

		return new VisionCompletion(response.toString(), VERSION);
	}

	private static ObjectNode surface(String id, long seed, int index) {
		ObjectNode surface = NODES.objectNode();
		surface.put("id", id);
		surface.put("coating", pick(COATING, seed, index * 7));
		surface.put("currentTone", pick(TONE, seed, index * 7 + 1));
		surface.put("fillerRatio", pick(BAND, seed, index * 7 + 2));
		surface.put("skimCoatRequired", Math.floorMod(seed + index, 5) == 0);
		surface.put("crackLevel", pick(CRACK, seed, index * 7 + 3));
		surface.put("moisture", pick(MOISTURE, seed, index * 7 + 4));
		surface.put("wallpaper", Math.floorMod(seed + index, 7) == 0);
		surface.put("confidence", confidence(seed, index * 7 + 5));
		return surface;
	}

	/**
	 * The object path of the first frame — {@code quotes/{request}/{room}/{photo}.jpg}. The query string
	 * is left out on purpose: a presigned URL's signature changes every time it is signed, and a fake
	 * that answered differently on a retry would be a fake nobody could reason about.
	 */
	private static long seedOf(VisionPrompt prompt) {
		String path = prompt.images().getFirst().read().url().getPath();
		return Math.abs((long) path.hashCode());
	}

	private static String pick(String[] values, long seed, int salt) {
		return values[(int) Math.floorMod(seed / (salt + 1) + salt, values.length)];
	}

	private static BigDecimal confidence(long seed, int salt) {
		// 0.60 to 0.95, at the scale numeric(4,3) will keep.
		return BigDecimal.valueOf(60 + Math.floorMod(seed + salt, 36), 2);
	}
}
