package com.burakpadr.decorating.quoting.adapter.out.notification;

import com.burakpadr.decorating.quoting.domain.model.TemplateCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns a template and some values into the text that goes over the wire (§13).
 *
 * <p>Both directions are errors. A placeholder nobody filled in reaches the customer as
 * {@code {link}} — sent, charged for, useless, and indistinguishable downstream from a message that
 * worked. A value nobody used means the caller believes it is sending something it is not, which is
 * what happens when a template is edited and its caller is not.
 */
@Component
public class SmsTemplates {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z]+)}");

	public String render(TemplateCode code, Map<String, String> values) {
		return render(code.name(), code.name(), values);
	}

	/**
	 * Renders a message that is not one of §13's eleven.
	 *
	 * <p>The OTP is the only one, and deliberately outside the catalogue: it is a transactional code,
	 * it appears in neither §13's list nor the workflow's §9 notification table, and adding a twelfth
	 * {@code TemplateCode} would change a count the spec, {@code api/CLAUDE.md}, decision 8 and two
	 * tests all state. It is still a file, still versioned with the artefact, and still measured
	 * against the segment budget — none of which required it to be a notification.
	 *
	 * @param name the file under {@code notifications/tr/}, without the extension
	 * @param describedAs what to call it in an error message
	 */
	public String render(String name, String describedAs, Map<String, String> values) {
		String template = read(name, describedAs);

		Set<String> used = new HashSet<>();
		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuilder body = new StringBuilder();
		while (matcher.find()) {
			String placeholder = matcher.group(1);
			String value = values.get(placeholder);
			if (value == null) {
				throw new IllegalStateException(
						describedAs + " has a placeholder nobody filled in: " + placeholder);
			}
			used.add(placeholder);
			matcher.appendReplacement(body, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(body);

		Set<String> unused = new HashSet<>(values.keySet());
		unused.removeAll(used);
		if (!unused.isEmpty()) {
			throw new IllegalArgumentException(describedAs + " has no placeholder for: " + unused);
		}
		// Templates end with a newline because files do; an SMS is one line.
		return body.toString().strip();
	}

	private String read(String name, String describedAs) {
		String path = "notifications/tr/" + name + ".txt";
		try (InputStream file = getClass().getClassLoader().getResourceAsStream(path)) {
			if (file == null) {
				throw new IllegalStateException("no template at " + path + " for " + describedAs);
			}
			return new String(file.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException unreadable) {
			throw new IllegalStateException("could not read " + path, unreadable);
		}
	}
}
