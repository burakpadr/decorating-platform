package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import java.util.UUID;

/**
 * Reading a photograph back, for the operator only (§9).
 *
 * <p>Separate from {@link CapturePhotos} because the actor is: the customer writes frames and never
 * reads them — their phone still has the originals — and the operator reads them and never writes.
 * Putting both on one port would mean one of the two rules had to be a runtime check inside a method
 * that serves both.
 */
public interface ReviewPhotos {

	/** A short-lived GET for one photograph. Short because a link is forwardable and a home is not. */
	PresignedUrl readable(UUID photoId);
}
