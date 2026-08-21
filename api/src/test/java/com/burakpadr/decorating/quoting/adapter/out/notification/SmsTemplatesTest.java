package com.burakpadr.decorating.quoting.adapter.out.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.quoting.domain.model.TemplateCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rendering an SMS from its template (§13).
 *
 * <p>The templates are files in the deployed artifact rather than rows, so a rollback rolls the wording
 * back with it — {@code notifications/README.md} says why. What is asserted here is the pair of things
 * that go wrong when text and code live apart: a template the code does not know about, and a
 * placeholder nobody substituted.
 */
class SmsTemplatesTest {

	private final SmsTemplates templates = new SmsTemplates();

	@Test
	@DisplayName("§13's ESTIMATE_SMS, rendered")
	void rendersTheEstimate() {
		String body = templates.render(TemplateCode.ESTIMATE_SMS, Map.of(
				"range", "45.241-57.580 TL",
				"link", "https://ornek.com/t/AbC123"));

		assertThat(body).isEqualTo(
				"Boya tahmininiz: 45.241-57.580 TL. Devam: https://ornek.com/t/AbC123");
	}

	@Test
	@DisplayName("a placeholder nobody filled in is a bug, not a message")
	void refusesAnUnsubstitutedPlaceholder() {
		// "{link}" reaching a customer is the failure this prevents: the SMS is sent, it is charged for,
		// and it is useless — and nothing downstream can tell it apart from a delivered message.
		assertThatThrownBy(() -> templates.render(TemplateCode.ESTIMATE_SMS,
						Map.of("range", "45.241-57.580 TL")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("link");
	}

	@Test
	@DisplayName("a value nobody asked for is a bug too, in the other direction")
	void refusesAnUnusedValue() {
		// Passing {estimate} to a template that has no {estimate} means the caller thinks it is sending
		// something it is not — usually because the template was edited and the caller was not.
		assertThatThrownBy(() -> templates.render(TemplateCode.ESTIMATE_SMS, Map.of(
						"range", "45.241-57.580 TL",
						"link", "https://ornek.com/t/AbC123",
						"estimate", "74.000 TL")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("estimate");
	}

	@Test
	@DisplayName("the rendered body carries no newline: an SMS is one line")
	void rendersOneLine() {
		String body = templates.render(TemplateCode.ESTIMATE_SMS,
				Map.of("range", "1 TL", "link", "x"));

		assertThat(body).doesNotContain("\n").isEqualTo(body.strip());
	}

	@Test
	@DisplayName("every template §13 names exists on disk, and every file on disk is named")
	void theEnumAndTheFilesAgree() throws IOException {
		// Deliberately duplicated — §13's table, the enum, and the files — so this is what notices. A
		// code with no file fails at send time, in production, on the one message that mattered; a file
		// with no code is wording nobody can reach and nobody will maintain.
		try (Stream<Path> files = Files.list(Path.of("src/main/resources/notifications/tr"))) {
			assertThat(files.map(path -> path.getFileName().toString().replace(".txt", "")).sorted())
					.containsExactlyElementsOf(
							Stream.of(TemplateCode.values()).map(Enum::name).sorted().toList());
		}
	}

	@Test
	@DisplayName("§13: QUOTE_READY must not contain an amount")
	void quoteReadyCarriesNoAmount() {
		// Not a rendering rule but a template rule, and this is where the templates are read. §13: a bare
		// number without the line-item breakdown gets judged out of context and the quote never gets
		// opened.
		String body = templates.render(TemplateCode.QUOTE_READY, Map.of("link", "https://x/y"));

		assertThat(body).doesNotContain("TL").doesNotContain("{estimate}").doesNotContain("{range}");
	}
}
