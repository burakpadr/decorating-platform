package com.burakpadr.decorating.quoting.adapter.out.vision;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code prompts/room-analysis/schema.json}, compiled once and used in both directions (§6).
 *
 * <p>The same file is sent to the provider as the structured-output schema and used to check what
 * comes back. That is deliberate rather than tidy: a response type restating the schema in Java would
 * be a second vocabulary to keep in step, and the schema is already keeping step with the domain enums
 * and the CHECK constraints ({@code RoomAnalysisSchemaTest} does that asking).
 *
 * <p>One schema for every prompt version. {@code v1.md} and its successors change how the room is
 * described; they do not change what a room may be described as, because that vocabulary is fixed by
 * §4.4's columns. A prompt version that needed a different shape would need a migration too.
 *
 * <p>Text that is not JSON comes back as a validation failure and not as an exception. Providers
 * prepend prose, and "the model answered something we cannot use" is one outcome with one handling —
 * retry once, then fail the job — however unusable it turned out to be.
 */
@Component
class RoomAnalysisSchema {

	static final String PATH = "prompts/room-analysis/schema.json";

	/**
	 * Floating point stays exact. A confidence lands in {@code numeric(4,3)} and a band width is derived
	 * from it, so 0.88 read back as 0.8799999999999999 is a difference that survives into a price.
	 */
	private final JsonMapper json = JsonMapper.builder()
			.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
			.build();
	private final Schema schema;
	private final String source;

	RoomAnalysisSchema() {
		this.source = read();
		this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
				.getSchema(source);
	}

	/** The schema as written, for handing to a provider that takes one. */
	String asJson() {
		return source;
	}

	/**
	 * Checks a response and hands back the parsed form only if there is nothing wrong with it.
	 *
	 * <p>One type rather than a validate-then-parse pair, so there is no way to read a field out of a
	 * response nobody checked: {@link Validation#parsed()} is null exactly when {@link
	 * Validation#problems()} is not empty.
	 */
	Validation validate(String response) {
		JsonNode parsed;
		try {
			parsed = json.readTree(response);
		}
		catch (JacksonException notJson) {
			return new Validation(null, List.of("the response is not JSON: " + notJson.getOriginalMessage()));
		}
		List<String> problems = schema.validate(parsed).stream().map(Error::getMessage).toList();
		return new Validation(problems.isEmpty() ? parsed : null, problems);
	}

	/** A response that was checked, and what was wrong with it. */
	record Validation(JsonNode parsed, List<String> problems) {

		boolean isUsable() {
			return problems.isEmpty();
		}
	}

	private static String read() {
		try (InputStream file = new ClassPathResource(PATH).getInputStream()) {
			return new String(file.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException missing) {
			// The artefact shipped without the schema, so every analysis this instance runs would be
			// believed unchecked. Worth failing at startup rather than at the first customer's photographs.
			throw new UncheckedIOException("the room analysis schema is missing from the artefact", missing);
		}
	}
}
