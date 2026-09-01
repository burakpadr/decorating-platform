package com.burakpadr.decorating.quoting.adapter.out.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import com.burakpadr.decorating.quoting.domain.model.VisionUnavailable;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Which model the artefact ships with (BOYA-47).
 *
 * <p>Worth its own test because the whole safety argument rests on it. The deployed default has to be
 * the one that refuses: an analysis nobody performed is a set of observations about a home no model
 * has seen, and every one of them becomes a quantity, then a price, then an invoice — with nothing on
 * the quote that says where it came from. An SMS nobody sent can be sent by hand; this cannot be
 * un-quoted, which is why {@code RecordingSmsSender}'s arrangement is deliberately not copied here.
 */
class VisionProviderSelectionTest {

	private final ApplicationContextRunner context = new ApplicationContextRunner()
			.withUserConfiguration(UnconfiguredVisionModel.class, FakeVisionModel.class);

	@Test
	@DisplayName("with nothing configured, the model refuses and no frame goes anywhere")
	void refusesByDefault() {
		context.run(started -> {
			assertThat(started).hasSingleBean(VisionModel.class)
					.hasSingleBean(UnconfiguredVisionModel.class)
					.doesNotHaveBean(FakeVisionModel.class);

			assertThatThrownBy(() -> started.getBean(VisionModel.class).complete(aPrompt()))
					.isInstanceOf(VisionUnavailable.class)
					.hasMessageContaining("BOYA-7");
		});
	}

	@Test
	@DisplayName("the fake is opt-in, and opting in replaces the refusal rather than joining it")
	void theFakeIsChosenExplicitly() {
		context.withPropertyValues("decorating.vision.provider=fake").run(started ->
				assertThat(started).hasSingleBean(VisionModel.class)
						.hasSingleBean(FakeVisionModel.class)
						.doesNotHaveBean(UnconfiguredVisionModel.class));
	}

	@Test
	@DisplayName("a provider named but not on the classpath leaves no model at all")
	void namingAnAbsentProviderLeavesNothing() {
		// Better than falling back to the refusing one: a deployment that asked for a provider and got
		// a stand-in has been told nothing, and would discover it one failed job at a time.
		context.withPropertyValues("decorating.vision.provider=anthropic").run(started ->
				assertThat(started).doesNotHaveBean(VisionModel.class));
	}

	private static VisionPrompt aPrompt() {
		return new VisionPrompt("instructions", "{}", List.of(new VisionImage("WALL_1",
				new PresignedUrl(URI.create("https://minio.test/a.jpg"), Duration.ofMinutes(5)))));
	}
}
