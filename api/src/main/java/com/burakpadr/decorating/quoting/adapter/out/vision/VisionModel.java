package com.burakpadr.decorating.quoting.adapter.out.vision;

/**
 * The one line the provider goes behind.
 *
 * <p>Everything else in this package is provider-independent — the versioned prompt, the schema, the
 * one retry §6 allows, the mapping onto findings — so choosing a provider is one class implementing
 * this and no edits anywhere else. That is not speculative flexibility: BOYA-7 is still open, the data
 * processing agreement for customer photographs is not signed, and the work above this interface had
 * no reason to wait for it.
 *
 * <p>Text in, text out. The response is not parsed here and is not trusted here; the caller validates
 * it against the schema before a field of it is believed.
 */
interface VisionModel {

	VisionCompletion complete(VisionPrompt prompt);
}
