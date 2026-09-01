package com.burakpadr.decorating.quoting.adapter.out.vision;

import java.util.List;

/**
 * One request to the model: what to do, what shape to answer in, and the frames.
 *
 * @param instructions the versioned prompt, verbatim from the artefact
 * @param responseSchema {@code schema.json}, for a provider that takes a structured-output schema.
 *     Handing it over is not a substitute for validating the answer — a provider that ignores it, or
 *     honours it loosely, fails silently and in the direction of a plausible response.
 * @param images every frame of the one room, in label order
 */
record VisionPrompt(String instructions, String responseSchema, List<VisionImage> images) {

	VisionPrompt {
		images = List.copyOf(images);
	}
}
