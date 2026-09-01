package com.burakpadr.decorating.quoting.adapter.out.vision;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The prompt, and the one thing that has to stay true of it (§4.4, decision 0006).
 *
 * <p>{@code prompts/room-analysis/<version>.md} is a versioned asset: the filename <em>is</em>
 * {@code room_analysis.prompt_version}, so a version naming a file the artefact does not carry would
 * attribute every analysis to a prompt nobody can read. The constructor is where that fails, and it
 * fails at startup rather than at the first customer's photographs.
 *
 * <p>The assertion with teeth is the second one. A field added to {@code schema.json} that nobody adds
 * to the prompt is a field the model is never asked for, so it omits it, so validation fails — on
 * every call, in every room, with a message about a missing property rather than about a prompt. The
 * two files have to be edited together and this is what asks them to be.
 */
class RoomAnalysisPromptTest {

	private static final Path SCHEMA = Path.of("src/main/resources/prompts/room-analysis/schema.json");

	private final RoomAnalysisPrompt prompt = new RoomAnalysisPrompt();

	@Test
	@DisplayName("the current version names a file the artefact carries, and the filename is the version")
	void theVersionIsAFileThatShipped() {
		assertThat(prompt.version()).isEqualTo("v1");
		assertThat(prompt.text()).isNotBlank();
		assertThat(Path.of("src/main/resources/prompts/room-analysis", prompt.version() + ".md"))
				.exists();
	}

	@Test
	@DisplayName("every field the schema requires is a field the prompt asks for")
	void theModelIsAskedForEverythingTheSchemaDemands() {
		// Matched as a backticked token, the way the prompt writes every field name. A bare substring
		// would find "id" inside "visible" and pass on a field the model was never told about.
		assertThat(requiredFieldsOfTheSchema())
				.allSatisfy(field -> assertThat(prompt.text())
						.as("`%s` is required by schema.json and never named in %s.md — the model will "
								+ "omit it and every response will fail validation", field, prompt.version())
						.contains("`" + field + "`"));
	}

	@Test
	@DisplayName("the maintainer's header is not sent to the model")
	void stripsTheEditorialHeader() {
		// The file opens with an HTML comment addressed to whoever edits it next — the version rule, what
		// is still draft about it, which decision amended it. Every token of that is paid for on every
		// room of every request, and it tells the model about our decision records.
		assertThat(prompt.text()).doesNotContain("<!--").doesNotContain("decision 0020");
		assertThat(prompt.text()).startsWith("You are surveying photographs");
	}

	@Test
	@DisplayName("the prompt still says the notes are Turkish")
	void theNotesRuleSurvivesEveryEdit() {
		// §6 states it explicitly because the model otherwise translates the whole output to one
		// language — and the field an operator actually reads is the one that would go to English.
		assertThat(prompt.text()).containsIgnoringCase("Turkish");
	}

	private static List<String> requiredFieldsOfTheSchema() {
		JsonNode schema;
		try {
			schema = JsonMapper.builder().build()
					.readTree(Files.readString(SCHEMA, StandardCharsets.UTF_8));
		}
		catch (IOException unreadable) {
			throw new AssertionError("cannot read " + SCHEMA, unreadable);
		}

		List<String> fields = new ArrayList<>();
		collectRequired(schema, fields);
		return fields;
	}

	private static void collectRequired(JsonNode node, List<String> into) {
		node.path("required").valueStream().map(JsonNode::stringValue).forEach(into::add);
		node.path("properties").propertyStream()
				.forEach(property -> collectRequired(property.getValue(), into));
		if (node.has("items")) {
			collectRequired(node.get("items"), into);
		}
	}
}
