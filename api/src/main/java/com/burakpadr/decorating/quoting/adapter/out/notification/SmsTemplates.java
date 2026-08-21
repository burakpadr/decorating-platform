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
		String template = read(code);

		Set<String> used = new HashSet<>();
		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuilder body = new StringBuilder();
		while (matcher.find()) {
			String name = matcher.group(1);
			String value = values.get(name);
			if (value == null) {
				throw new IllegalStateException(
						code + " has a placeholder nobody filled in: " + name);
			}
			used.add(name);
			matcher.appendReplacement(body, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(body);

		Set<String> unused = new HashSet<>(values.keySet());
		unused.removeAll(used);
		if (!unused.isEmpty()) {
			throw new IllegalArgumentException(code + " has no placeholder for: " + unused);
		}
		// Templates end with a newline because files do; an SMS is one line.
		return body.toString().strip();
	}

	private String read(TemplateCode code) {
		String path = "notifications/tr/" + code.name() + ".txt";
		try (InputStream file = getClass().getClassLoader().getResourceAsStream(path)) {
			if (file == null) {
				throw new IllegalStateException("no template at " + path);
			}
			return new String(file.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException unreadable) {
			throw new IllegalStateException("could not read " + path, unreadable);
		}
	}
}
