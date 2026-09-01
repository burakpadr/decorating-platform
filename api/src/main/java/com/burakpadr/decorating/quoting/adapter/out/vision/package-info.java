/**
 * Vision provider adapter. Validates model output against the JSON schema before
 * anything is persisted.
 *
 * <p>Provider-independent by design (BOYA-47). Everything here — the versioned prompt, the schema, the
 * one retry §6 allows, the mapping onto findings — works whichever provider is eventually chosen;
 * {@code VisionModel} is the single interface that provider implements, selected by
 * {@code decorating.vision.provider}. The default is the one that refuses: no provider is chosen and
 * no data processing agreement is signed (BOYA-7), so no customer photograph goes anywhere.
 */
package com.burakpadr.decorating.quoting.adapter.out.vision;
