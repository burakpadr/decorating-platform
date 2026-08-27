package com.burakpadr.decorating.quoting.domain.model;

/**
 * The notice a customer is shown before consenting, and the version that names it.
 *
 * <p>The two travel together on purpose. A version without its text is a string nobody can resolve
 * back to what was agreed, and a text without its version is a paragraph that can be edited into
 * saying something else while old grants go on pointing at it. §12: "Consent is versioned
 * ({@code text_version}) so you know which notice each grant referred to."
 *
 * <p>{@code version} is the filename of the resource it was read from, exactly as
 * {@code room_analysis.prompt_version} is (decision 0006, extended by 0018).
 */
public record ConsentNotice(ConsentType type, String version, String body) {

	public ConsentNotice {
		if (type == null) {
			throw new IllegalArgumentException("a notice is a notice about something");
		}
		if (version == null || version.isBlank()) {
			throw new IllegalArgumentException("a notice nobody can name is a notice nobody can honour");
		}
		if (body == null || body.isBlank()) {
			throw new IllegalArgumentException("an empty notice is not a notice");
		}
	}
}
