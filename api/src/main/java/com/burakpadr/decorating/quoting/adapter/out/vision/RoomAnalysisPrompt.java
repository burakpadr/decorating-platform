package com.burakpadr.decorating.quoting.adapter.out.vision;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The room analysis prompt, read from the deployed artefact (§4.4, decision 0006).
 *
 * <p>The current version is a constant here rather than a configuration property, for the reason
 * {@code ClasspathConsentNotices} gives about notices: a property could name a version the running
 * artefact does not contain, and then every analysis would be attributed to a prompt nobody can read.
 * Bumping it happens in the same commit that adds the next file, so a rollback of the application
 * rolls the prompt back with it.
 *
 * <p>A released version is never edited in place, because {@code room_analysis.prompt_version} makes a
 * prompt an input to persisted rows in exactly the way a price book version is an input to persisted
 * quotes. {@code v1.md} is what {@code prompt_version = "v1"} refers to, for as long as any row says
 * so. (Decision 0020 amended {@code v1.md} before that rule had anything to protect: no row had ever
 * referenced it, because this class is the first thing that loads it.)
 */
@Component
class RoomAnalysisPrompt {

	/** The version every analysis this artefact produces is recorded under. Add {@code v2.md}, then bump. */
	private static final String CURRENT = "v1";

	private final String text;

	RoomAnalysisPrompt() {
		this.text = read(CURRENT);
	}

	String version() {
		return CURRENT;
	}

	String text() {
		return text;
	}

	/**
	 * The file, minus the header addressed to whoever edits it next.
	 *
	 * <p>Every prompt opens with an HTML comment carrying the version rule and whatever is still draft
	 * about the wording. None of that is instruction: it is paid for in tokens on every room of every
	 * request, and it tells a provider about our decision records. Stripped here rather than kept in a
	 * second file, because a maintainer's note is worth most when it is the first thing in the file it
	 * is about.
	 */
	private static String read(String version) {
		String path = "prompts/room-analysis/" + version + ".md";
		try {
			String file = new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
			return file.startsWith("<!--")
					? file.substring(file.indexOf("-->") + "-->".length()).strip()
					: file;
		}
		catch (IOException missing) {
			// Unrecoverable and worth saying plainly: the constant above names a file the build did not
			// ship, so every analysis this instance recorded would cite a prompt that does not exist.
			throw new UncheckedIOException("room analysis prompt missing from the artefact: " + path, missing);
		}
	}
}
