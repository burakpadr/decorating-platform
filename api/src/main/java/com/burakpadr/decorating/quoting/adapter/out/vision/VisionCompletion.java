package com.burakpadr.decorating.quoting.adapter.out.vision;

/**
 * What the provider said, and which model said it.
 *
 * <p>{@code modelVersion} is written to {@code room_analysis.model_version} and is mandatory for the
 * same reason {@code prompt_version} is (§4.4): a calibration set that cannot tell which model
 * produced a finding cannot compare two of them. It is the provider's own identifier, not a name we
 * chose — "the model that was current in March" is not an answer anybody can act on later.
 */
record VisionCompletion(String text, String modelVersion) {}
