package com.burakpadr.decorating.quoting.domain.model;

import java.util.UUID;

/**
 * One frame as the model is shown it (§6): a name, and where the object lives.
 *
 * <p>The label is the join. §6's output carries a surface {@code id} and a {@code photoId}, and both
 * are only meaningful because the frame went in under a name the response could repeat — so a label is
 * assigned once, here, and the same list is what maps the answer back.
 *
 * <p>No bytes. {@code storageKey} is what the adapter presigns a short read of; the photograph does not
 * pass through the JVM on the way to the model any more than it did on the way in (§9).
 */
public record LabelledPhoto(String label, UUID photoId, String storageKey) {

	public LabelledPhoto {
		if (label == null || photoId == null || storageKey == null) {
			throw new IllegalArgumentException("a labelled frame is a name, an id and a key");
		}
	}
}
