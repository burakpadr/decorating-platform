package com.burakpadr.decorating.quoting.adapter.out.notice;

import com.burakpadr.decorating.quoting.domain.model.ConsentNotice;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import com.burakpadr.decorating.quoting.domain.port.out.ConsentNotices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The notice texts, read from the deployed artefact (decision 0018, extending 0006).
 *
 * <p>The current version is a constant here rather than a configuration property, and that is the
 * whole point of the arrangement: a property could name a version the running artefact does not
 * contain, which is a grant referring to a text nobody can produce. Bumping it happens in the same
 * commit that adds the next file, so a rollback of the application rolls the notice back with it.
 *
 * <p>A released version is never edited in place. {@code v1.md} is what {@code text_version = "v1"}
 * refers to, for as long as any row says so.
 */
@Component
class ClasspathConsentNotices implements ConsentNotices {

	/** Type to the version currently published. Add a line when a type gains its first notice. */
	private static final Map<ConsentType, String> CURRENT =
			Map.of(ConsentType.PROCESSING, "v1");

	@Override
	public ConsentNotice current(ConsentType type) {
		String version = CURRENT.get(type);
		if (version == null) {
			// RETENTION_FOR_IMPROVEMENT today: the column accepts it, §16 has not written it yet.
			throw new IllegalStateException("no notice is published for " + type + " yet");
		}
		return new ConsentNotice(type, version, read(type, version));
	}

	private static String read(ConsentType type, String version) {
		String path = "consent/tr/%s/%s.md"
				.formatted(type.name().toLowerCase(Locale.ROOT).replace('_', '-'), version);
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException missing) {
			// Unrecoverable and worth saying plainly: the constant above names a file the build did not
			// ship, so every grant this instance records would refer to nothing.
			throw new UncheckedIOException("consent notice missing from the artefact: " + path, missing);
		}
	}
}
