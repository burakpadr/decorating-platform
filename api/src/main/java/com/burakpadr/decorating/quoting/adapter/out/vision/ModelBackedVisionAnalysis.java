package com.burakpadr.decorating.quoting.adapter.out.vision;

import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysisRequest;
import com.burakpadr.decorating.quoting.domain.model.UnusableAnalysis;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoStorage;
import com.burakpadr.decorating.quoting.adapter.out.vision.RoomAnalysisSchema.Validation;
import com.burakpadr.decorating.quoting.domain.port.out.VisionAnalysisPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * One room, one call, one retry (§6, §8, BOYA-47).
 *
 * <p>Everything here is provider-independent; {@link VisionModel} is where the provider goes. What
 * this class owns is the sequence §6 specifies and the failure handling §8 depends on.
 *
 * <p><b>Validate before believing.</b> The schema is sent to the provider and the answer is checked
 * against it anyway. A provider that honours structured output loosely does not announce it — the
 * response simply comes back plausible and slightly wrong, which is the one failure mode this system
 * cannot absorb, because a finding becomes a price and a price becomes an invoice.
 *
 * <p><b>One retry, and only for an answer.</b> §6: "on validation failure, retry once then fail the
 * job." An outage is not retried here at all. The analysis job already retries with 2^attempts minutes
 * between tries (§8), and doubling a timeout inside the call would spend two provider timeouts before
 * the job has waited once — with the row still reading RUNNING the whole time.
 *
 * <p><b>Nothing partial.</b> Either a validated response maps whole, or this throws. There is no
 * return path carrying the fields that happened to parse.
 */
@Component
class ModelBackedVisionAnalysis implements VisionAnalysisPort {

	private static final Logger log = LoggerFactory.getLogger(ModelBackedVisionAnalysis.class);

	private final VisionModel model;
	private final RoomAnalysisPrompt prompts;
	private final RoomAnalysisSchema schema;
	private final PhotoStorage storage;

	ModelBackedVisionAnalysis(VisionModel model, RoomAnalysisPrompt prompts, RoomAnalysisSchema schema,
			PhotoStorage storage) {
		this.model = model;
		this.prompts = prompts;
		this.schema = schema;
		this.storage = storage;
	}

	@Override
	public RoomAnalysis analyse(RoomAnalysisRequest request) {
		VisionPrompt prompt = new VisionPrompt(prompts.text(), schema.asJson(), images(request));

		VisionCompletion answer = model.complete(prompt);
		Validation checked = schema.validate(answer.text());

		if (!checked.isUsable()) {
			log.warn("room {} came back unusable, asking once more: {}",
					request.roomId(), checked.problems());
			answer = model.complete(prompt);
			checked = schema.validate(answer.text());
		}
		if (!checked.isUsable()) {
			throw new UnusableAnalysis(
					"room " + request.roomId() + " was analysed twice and neither answer validated",
					checked.problems());
		}

		return RoomAnalysisMapper.map(request, checked.parsed(), answer.text(),
				prompts.version(), answer.modelVersion());
	}

	/**
	 * The frames as short-lived reads. Signed here rather than carried on the request because a URL
	 * with a lifetime on it should be made as late as it can be — a queued job may sit for minutes
	 * behind four others, and a link signed when the job was created could expire before it is used.
	 */
	private List<VisionImage> images(RoomAnalysisRequest request) {
		return request.photos().stream()
				.map(photo -> new VisionImage(photo.label(), storage.presignGet(photo.storageKey())))
				.toList();
	}
}
