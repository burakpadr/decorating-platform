package com.burakpadr.decorating.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the SMS segment budget (§13).
 *
 * <p>Turkish characters are the whole reason this test exists. GSM-7 carries 160 characters per
 * segment, but {@code ı İ ğ Ğ ş Ş ç} are outside it, so a single one of them drops the entire
 * message to UCS-2 and 70 characters. Billing is per segment, which means correctly spelled Turkish
 * under 70 characters costs exactly what de-accented Turkish under 160 costs — so templates are
 * spelled properly and kept short, rather than mangled to stay in GSM-7.
 *
 * <p>Length is measured <em>after</em> placeholder substitution with realistic values, because that
 * is what goes over the wire. A template that fits before substitution and spills after is the
 * failure mode this catches.
 */
class SmsSegmentBudgetTest {

	private static final Path TEMPLATES = Path.of("src/main/resources/notifications/tr");

	/**
	 * GSM 03.38 basic set plus the extension table. Note {@code ö ü ä Ö Ü Ñ à Ç} are inside it and
	 * lowercase {@code ç} is not — which is why the naive "avoid Turkish letters" rule is wrong.
	 */
	private static final String GSM_BASIC =
			"@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
					+ "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";

	/** Each of these costs two GSM-7 characters. */
	private static final String GSM_EXTENDED = "^{}\\[~]|€";

	private static final Map<String, String> PLACEHOLDERS = Map.of(
			// A real handoff link, not a short example: /devam/ plus a 32-byte base64url token is 43
			// characters of token alone, and the budget this test defends is measured on what goes over the
			// wire. The old 26-character placeholder made every template look 45 characters safer than it is.
			// A real handoff link: /devam/ plus a 22-character token (128 bits, base64url). The budget this
			// test defends is measured on what goes over the wire, and the old 26-character example made
			// every template look 45 characters safer than it was — three were silently over.
			//
			// The domain is part of the measurement. QUOTE_READY lands on exactly 70 UCS-2 characters with
			// a 15-character domain, so a longer one costs a second segment on the most-sent customer
			// message in the set. That is a real constraint on the domain choice, not a detail.
			"{link}", "https://boyateklifi.com/devam/NDrj6BbXwVeFcBV0ZiZNEa",
			"{range}", "68.000-86.000 TL",
			"{date}", "24.09.2026",
			"{slot}", "Sabah",
			"{district}", "Küçükçekmece",
			"{room}", "Salon 2. duvar",
			"{estimate}", "74.000 TL",
			"{hours}", "26");

	/**
	 * Budgeted segments per template.
	 *
	 * <p>{@code RECAPTURE_NEEDED} is allowed two on purpose. Spec §6 requires the request to name
	 * the frame that failed — "the second wall of the living room came out dark", not "retake your
	 * photos" — and a room label plus a link does not fit in 70 UCS-2 characters. It is also the
	 * rarest message in the set, sent only when analysis rejects a photograph. Specificity is worth
	 * more than the segment.
	 */
	private static final Map<String, Integer> BUDGET = new LinkedHashMap<>();

	static {
		// Customer-facing, in the order the customer meets them (§9).
		BUDGET.put("ESTIMATE_SMS", 1);
		BUDGET.put("RECAPTURE_NEEDED", 2);
		BUDGET.put("SURVEY_NEEDED", 1);
		BUDGET.put("QUOTE_READY", 1);
		BUDGET.put("EXPIRY_REMINDER", 1);
		BUDGET.put("QUOTE_EXPIRED", 1);
		BUDGET.put("ACCEPT_CONFIRMED", 1);

		// Operator-facing. The workflow document (§9) names four; SMS or WhatsApp rather than a push
		// notification, because the operator does not keep the panel open.
		// Two segments each, and worth it. Both carry a link, and a link plus Turkish prose does not fit
		// in 70 UCS-2 characters — the alternative is de-accenting, which §13 rejects as both wrong and
		// ugly. These go to the business rather than to customers: one per request, and what would have to
		// be cut is the district, which is the only thing that makes the message actionable on a phone.
		BUDGET.put("OPERATOR_NEW_REQUEST", 2);
		BUDGET.put("OPERATOR_QUOTE_ACCEPTED", 2);
		BUDGET.put("OPERATOR_CALLBACK_OVERDUE", 1);
		BUDGET.put("OPERATOR_DELETION_REQUEST", 1);
	}

	@Test
	@DisplayName("every budgeted template exists and none exceeds its segment budget")
	void templatesStayWithinBudget() throws IOException {
		try (Stream<Path> files = Files.list(TEMPLATES)) {
			List<String> found = files
					.map(p -> p.getFileName().toString().replace(".txt", ""))
					.sorted()
					.toList();
			assertThat(found)
					.as("every template_code in §13 needs a file, and no file may exist without one")
					.containsExactlyInAnyOrderElementsOf(BUDGET.keySet());
		}

		StringBuilder report = new StringBuilder("\nSMS segment budget\n");
		boolean overBudget = false;

		for (Map.Entry<String, Integer> entry : BUDGET.entrySet()) {
			String code = entry.getKey();
			String body = Files.readString(TEMPLATES.resolve(code + ".txt"), StandardCharsets.UTF_8).strip();

			assertThat(body).as("%s must not be empty", code).isNotEmpty();
			assertThat(body)
					.as("%s must not leak an internal figure to the customer", code)
					.doesNotContain("total_cost", "margin");

			String wire = body;
			for (Map.Entry<String, String> p : PLACEHOLDERS.entrySet()) {
				wire = wire.replace(p.getKey(), p.getValue());
			}
			assertThat(wire)
					.as("%s still contains an unsubstituted placeholder — add it to PLACEHOLDERS", code)
					.doesNotContain("{");

			int segments = segmentsFor(wire);
			boolean ucs2 = requiresUcs2(wire);
			if (segments > entry.getValue()) {
				overBudget = true;
			}
			report.append(String.format(
					"  %-22s %-6s %3d chars  %d segment(s), budget %d%s%n",
					code, ucs2 ? "UCS-2" : "GSM-7", wire.length(), segments, entry.getValue(),
					segments > entry.getValue() ? "   <-- OVER" : ""));
		}

		assertThat(overBudget)
				.as("a template grew past its segment budget. Shorten it, or raise the budget "
						+ "deliberately with a comment explaining what is worth the extra segment.%s",
						report)
				.isFalse();
	}

	/** QUOTE_READY must never carry the amount: judged out of context, the quote never gets opened. */
	@Test
	@DisplayName("QUOTE_READY carries a link and no amount")
	void quoteReadyCarriesNoAmount() throws IOException {
		String body = Files.readString(TEMPLATES.resolve("QUOTE_READY.txt"), StandardCharsets.UTF_8);

		assertThat(body).contains("{link}");
		assertThat(body).doesNotContain("{range}", "{estimate}");
	}

	private static boolean requiresUcs2(String text) {
		return text.chars().anyMatch(c ->
				GSM_BASIC.indexOf(c) < 0 && GSM_EXTENDED.indexOf(c) < 0);
	}

	private static int segmentsFor(String text) {
		if (requiresUcs2(text)) {
			return text.length() <= 70 ? 1 : ceilDiv(text.length(), 67);
		}
		int units = text.chars().map(c -> GSM_EXTENDED.indexOf(c) >= 0 ? 2 : 1).sum();
		return units <= 160 ? 1 : ceilDiv(units, 153);
	}

	private static int ceilDiv(int a, int b) {
		return (a + b - 1) / b;
	}
}
