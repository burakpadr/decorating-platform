package com.burakpadr.decorating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The published contract, checked against the routes it describes.
 *
 * <p>{@code api-client} is generated from this document, so a wrong description is not a documentation
 * problem — it is a client that cannot call the endpoint. The two failures below both happened: a
 * handler that takes its id through an argument resolver rather than {@code @PathVariable} left
 * springdoc with no path parameter to declare and, worse, with a resolver-only type it published as a
 * required query parameter. The generated TypeScript then typed the path parameters as {@code undefined}
 * and the build failed on the far side of the repo, a long way from the cause.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractTest {

	/** Types that exist to be resolved from the request, and must never appear as parameters. */
	private static final List<String> RESOLVED_NOT_SENT = List.of("OwnedQuoteRequest");

	@Autowired
	private MockMvc mvc;

	private JsonNode paths() throws Exception {
		String body = mvc.perform(get("/v3/api-docs"))
				.andReturn().getResponse().getContentAsString();
		return new ObjectMapper().readTree(body).get("paths");
	}

	@Test
	@DisplayName("every templated path declares the parameter its template names")
	void everyTemplatedPathDeclaresItsVariables() throws Exception {
		JsonNode paths = paths();
		List<String> undeclared = new ArrayList<>();

		paths.fieldNames().forEachRemaining(path -> {
			for (String variable : templateVariables(path)) {
				paths.get(path).fieldNames().forEachRemaining(verb -> {
					if (!isOperation(verb)) {
						return;
					}
					boolean declared = false;
					JsonNode parameters = paths.get(path).get(verb).get("parameters");
					if (parameters != null) {
						for (JsonNode parameter : parameters) {
							declared |= "path".equals(parameter.path("in").asText())
									&& variable.equals(parameter.path("name").asText());
						}
					}
					if (!declared) {
						undeclared.add(verb.toUpperCase() + " " + path + " {" + variable + "}");
					}
				});
			}
		});

		assertThat(undeclared)
				.as("a client generated from this document cannot fill a path variable the document does "
						+ "not mention")
				.isEmpty();
	}

	@Test
	@DisplayName("a type the server resolves for itself is never published as something to send")
	void resolvedTypesAreNotParameters() throws Exception {
		JsonNode paths = paths();
		List<String> leaked = new ArrayList<>();

		paths.fieldNames().forEachRemaining(path -> paths.get(path).fieldNames()
				.forEachRemaining(verb -> {
					if (!isOperation(verb)) {
						return;
					}
					JsonNode parameters = paths.get(path).get(verb).get("parameters");
					if (parameters == null) {
						return;
					}
					for (JsonNode parameter : parameters) {
						String schema = parameter.path("schema").path("$ref").asText();
						for (String resolved : RESOLVED_NOT_SENT) {
							if (schema.endsWith("/" + resolved)) {
								leaked.add(verb.toUpperCase() + " " + path + " → "
										+ parameter.path("name").asText() + " in "
										+ parameter.path("in").asText());
							}
						}
					}
				}));

		assertThat(leaked)
				.as("%s is resolved from the session cookie and the path; published as a parameter it "
						+ "tells every caller to send something the server would ignore", RESOLVED_NOT_SENT)
				.isEmpty();
	}

	private static boolean isOperation(String verb) {
		return List.of("get", "put", "post", "delete", "patch", "head", "options").contains(verb);
	}

	private static List<String> templateVariables(String path) {
		List<String> names = new ArrayList<>();
		java.util.regex.Matcher matcher =
				java.util.regex.Pattern.compile("\\{([^}]+)}").matcher(path);
		while (matcher.find()) {
			names.add(matcher.group(1));
		}
		return names;
	}
}
