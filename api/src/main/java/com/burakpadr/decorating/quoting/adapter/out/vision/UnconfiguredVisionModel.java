package com.burakpadr.decorating.quoting.adapter.out.vision;

import com.burakpadr.decorating.quoting.domain.model.VisionUnavailable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The model nobody has chosen yet (BOYA-47, pending BOYA-7).
 *
 * <p>No provider is selected and the data processing agreement for customer photographs is not signed,
 * so nothing may be sent anywhere. This refuses, and the analysis job records the reason and retries on
 * its own schedule (§8) — visible, explicable, and stopped.
 *
 * <p>The alternative deserves naming, because it is the tempting one: making {@code FakeVisionAnalysis}
 * the default the way {@code RecordingSmsSender} is the default for SMS. It is not the same situation.
 * An SMS nobody sent is a message an operator can send by hand; an analysis nobody performed is a set
 * of findings about a home no model has ever seen, and every one of them becomes a quantity and then a
 * price. A deployed artefact that invents observations when its provider is missing is worse than one
 * that stops, so the fake is opt-in ({@code decorating.vision.provider=fake}) and this is what ships.
 *
 * <p>Selected by {@code decorating.vision.provider}, which defaults to {@code none}. A property rather
 * than {@code @ConditionalOnMissingBean} because which of these ships is the whole safety argument, and
 * bean-ordering is not the place to settle it: whether customer photographs go to a provider has to be
 * something a deployment states, not something the classpath decides.
 */
@Component
@ConditionalOnProperty(name = "decorating.vision.provider", havingValue = "none", matchIfMissing = true)
class UnconfiguredVisionModel implements VisionModel {

	@Override
	public VisionCompletion complete(VisionPrompt prompt) {
		throw new VisionUnavailable("no vision provider is configured (BOYA-7): "
				+ prompt.images().size() + " frames were not sent anywhere");
	}
}
