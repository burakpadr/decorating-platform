package com.burakpadr.decorating.quoting.adapter.out.vision;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.quoting.domain.model.Coating;
import com.burakpadr.decorating.quoting.domain.model.CrackLevel;
import com.burakpadr.decorating.quoting.domain.model.FillerBand;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The response schema, and the three places its vocabulary has to be the same one (§6, §4.4).
 *
 * <p>{@code schema.json} is the contract in both directions: it is what the provider is asked to
 * conform to and what the answer is checked against before a single field of it is believed. So the
 * failure it has to be protected from is not a malformed response — a validator catches that — but the
 * schema quietly disagreeing with the enums the answer is mapped onto and the CHECK constraints the
 * row is written under. Three lists spelling the same vocabulary three times, and nothing comparing
 * them, is how {@code ceiling.staining} came to offer {@code LIGHT} and {@code HEAVY} while
 * {@link com.burakpadr.decorating.quoting.domain.model.CeilingFinding#isRisk()} — the predicate ADR
 * 0017 wrote so an active leak goes to a survey rather than to a price — waited for an {@code ACTIVE}
 * the model had no way to say. Decision 0020.
 *
 * <p>This is the same guard as {@code districts.spec.ts}: where a comment would ask two files to stay
 * in step, a test asks.
 */
class RoomAnalysisSchemaTest {

	private static final Path SCHEMA = Path.of("src/main/resources/prompts/room-analysis/schema.json");
	private static final Path BASELINE = Path.of("src/main/resources/db/migration/V1__baseline.sql");

	private final JsonMapper json = JsonMapper.builder().build();
	private final RoomAnalysisSchema schema = new RoomAnalysisSchema();

	// -----------------------------------------------------------------------------------------------
	// The schema against an instance
	// -----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("§6's worked example is accepted")
	void acceptsTheWorkedExample() {
		assertThat(problemsIn(example())).isEmpty();
	}

	@Test
	@DisplayName("a missing required field is refused, and the message names it")
	void refusesAMissingField() {
		String withoutConfidence = example().replace("\"confidence\": 0.83,", "");

		assertThat(problemsIn(withoutConfidence))
				.anySatisfy(error -> assertThat(error).contains("confidence"));
	}

	@Test
	@DisplayName("a value outside the vocabulary is refused")
	void refusesAnInventedEnumValue() {
		// A model that answers PLASTERBOARD instead of PAINTED has not been understood, and the surface
		// would map onto nothing. Better a failed job than a wall priced as if it were paintable.
		assertThat(problemsIn(example().replace("\"PAINTED\"", "\"PLASTERBOARD\"")))
				.isNotEmpty();
	}

	@Test
	@DisplayName("a square metre volunteered by the model is refused")
	void refusesAnyFieldTheSchemaDidNotAskFor() {
		// The one rule the whole arrangement rests on: observations, never quantities. additionalProperties
		// is false so the model cannot smuggle a number in beside them and have something downstream pick
		// it up. The failure this prevents is not a bad total — it is a total from a source nobody audits.
		String withAnArea = example().replace(
				"\"doorCount\": 2,", "\"doorCount\": 2, \"wallAreaM2\": 41.5,");

		assertThat(problemsIn(withAnArea))
				.anySatisfy(error -> assertThat(error).contains("wallAreaM2"));
	}

	@Test
	@DisplayName("text that is not JSON at all fails as a validation failure, not as a crash")
	void refusesSomethingThatIsNotJson() {
		// Providers prepend prose to JSON. That has to be an invalid response the caller retries once,
		// not a parse exception thrown from a different layer with a different meaning.
		assertThat(problemsIn("Elbette! İşte oda analizi:")).isNotEmpty();
	}

	// -----------------------------------------------------------------------------------------------
	// The schema against the domain enums and the database
	// -----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("every vocabulary in the schema is the domain enum it maps onto")
	void schemaAgreesWithTheDomain() {
		assertThat(enumAt("/properties/roomType")).isEqualTo(namesOf(RoomType.class));
		assertThat(enumAt("/properties/furnishing")).isEqualTo(namesOf(Furnishing.class));

		String surface = "/properties/surfaces/items/properties/";
		assertThat(enumAt(surface + "coating")).isEqualTo(namesOf(Coating.class));
		assertThat(enumAt(surface + "currentTone")).isEqualTo(namesOf(Tone.class));
		assertThat(enumAt(surface + "fillerRatio")).isEqualTo(namesOf(FillerBand.class));
		assertThat(enumAt(surface + "crackLevel")).isEqualTo(namesOf(CrackLevel.class));
		assertThat(enumAt(surface + "moisture")).isEqualTo(namesOf(Moisture.class));

		// The ceiling asks the same physical question as a wall — is it dry, was it once wet, is it wet
		// now — so it answers in the same three words. ADR 0017 made ACTIVE the trigger for a survey;
		// a ceiling vocabulary with no ACTIVE in it made that rule unreachable (decision 0020).
		assertThat(enumAt("/properties/ceiling/properties/staining")).isEqualTo(namesOf(Moisture.class));
		assertThat(enumAt("/properties/ceiling/properties/fillerRatio"))
				.isEqualTo(namesOf(FillerBand.class));
	}

	@Test
	@DisplayName("every vocabulary in the schema is the CHECK constraint the row is written under")
	void schemaAgreesWithTheDatabase() {
		String surface = "/properties/surfaces/items/properties/";
		assertThat(enumAt(surface + "id")).isEqualTo(check("surface_finding_surface_check"));
		assertThat(enumAt(surface + "coating")).isEqualTo(check("surface_finding_coating_check"));
		assertThat(enumAt(surface + "currentTone")).isEqualTo(check("surface_finding_tone_check"));
		assertThat(enumAt(surface + "fillerRatio")).isEqualTo(check("surface_finding_filler_check"));
		assertThat(enumAt(surface + "crackLevel")).isEqualTo(check("surface_finding_crack_check"));
		assertThat(enumAt(surface + "moisture")).isEqualTo(check("surface_finding_moisture_check"));

		assertThat(enumAt("/properties/furnishing")).isEqualTo(check("room_analysis_furnishing_check"));
		assertThat(enumAt("/properties/ceiling/properties/staining"))
				.isEqualTo(check("room_analysis_ceiling_staining_check"));
		assertThat(enumAt("/properties/ceiling/properties/fillerRatio"))
				.isEqualTo(check("room_analysis_ceiling_filler_check"));
	}

	// -----------------------------------------------------------------------------------------------

	private List<String> problemsIn(String response) {
		return schema.validate(response).problems();
	}

	private List<String> enumAt(String pointer) {
		JsonNode values = read(SCHEMA).at(pointer + "/enum");
		assertThat(values.isArray()).as("no enum at %s", pointer).isTrue();
		return values.valueStream().map(JsonNode::stringValue).sorted().toList();
	}

	private static List<String> namesOf(Class<? extends Enum<?>> type) {
		return Arrays.stream(type.getEnumConstants()).map(Enum::name).sorted().toList();
	}

	/**
	 * The value list of one named CHECK constraint, read out of the migration rather than restated.
	 * Every migration is searched, so a constraint added in a later file is found where it was added.
	 */
	private static List<String> check(String constraint) {
		Pattern values = Pattern.compile(
				"CONSTRAINT\\s+" + constraint + "\\s+CHECK\\s*\\([^(]*IN\\s*\\(([^)]*)\\)");
		try (Stream<Path> migrations = Files.list(BASELINE.getParent())) {
			for (Path migration : migrations.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
				Matcher found = values.matcher(text(migration));
				if (found.find()) {
					return Arrays.stream(found.group(1).split(","))
							.map(value -> value.strip().replace("'", ""))
							.sorted()
							.toList();
				}
			}
		}
		catch (IOException unreadable) {
			throw new AssertionError("cannot read the migrations", unreadable);
		}
		throw new AssertionError("no migration declares " + constraint);
	}

	private JsonNode read(Path file) {
		return json.readTree(text(file));
	}

	private static String text(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException unreadable) {
			throw new AssertionError("cannot read " + file, unreadable);
		}
	}

	/** §6's published example, which is also what the fixtures downstream are built from. */
	static String example() {
		return """
				{
				  "roomType": "LIVING_ROOM",
				  "surfaces": [{
				    "id": "WALL_1",
				    "coating": "PAINTED",
				    "currentTone": "DARK",
				    "fillerRatio": "MEDIUM",
				    "skimCoatRequired": false,
				    "crackLevel": "HAIRLINE",
				    "moisture": "NONE",
				    "wallpaper": false,
				    "confidence": 0.88
				  }],
				  "ceiling": {
				    "cornice": true,
				    "downlightCount": 6,
				    "staining": "STAIN",
				    "fillerRatio": "LOW",
				    "confidence": 0.79
				  },
				  "furnishing": "FURNISHED",
				  "doorCount": 2,
				  "windowCount": 3,
				  "radiatorCount": 1,
				  "confidence": 0.83,
				  "unusablePhotos": [],
				  "notes": ["sol duvarda priz hizasında çatlak"]
				}
				""";
	}
}
