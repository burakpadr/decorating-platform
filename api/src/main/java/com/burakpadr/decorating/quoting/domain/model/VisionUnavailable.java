package com.burakpadr.decorating.quoting.domain.model;

/**
 * The vision provider could not be reached, or none is configured.
 *
 * <p>Distinct from {@link UnusableAnalysis} because the two want opposite handling. Nothing was said
 * here, so trying again later is exactly right and the analysis job's backoff (§8) is where that
 * happens. Retrying inside the call would multiply an outage by the wait.
 */
public class VisionUnavailable extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public VisionUnavailable(String message) {
		super(message);
	}

	public VisionUnavailable(String message, Throwable cause) {
		super(message, cause);
	}
}
