package com.burakpadr.decorating.quoting.domain.model;

import java.net.URI;
import java.time.Duration;

/**
 * A URL that stands in for a credential, and the window it stands in for one (§9).
 *
 * <p>The lifetime travels with the URL because it is the interesting half. A presigned PUT is a
 * write the holder can perform without an account, and a presigned GET is a photograph of somebody's
 * home that anybody holding the link can read — so how long it lives is a fact the caller has to be
 * able to state, not a number buried in the adapter that signed it.
 */
public record PresignedUrl(URI url, Duration expiresIn) {

	public PresignedUrl {
		if (url == null || expiresIn == null) {
			throw new IllegalArgumentException("a presigned url expires");
		}
	}
}
