package com.burakpadr.decorating.quoting.domain.model;

/**
 * Frames are still missing (§3's guard on the submit arrow).
 *
 * <p>Its own type because the answer is a screen the customer can act on: the capture list, with the
 * outstanding frames still on it. The counts travel with it so the screen can say which, rather than
 * making the customer count.
 */
public class CaptureIncomplete extends RuntimeException {

	private final int required;
	private final int taken;

	public CaptureIncomplete(int required, int taken) {
		super("the capture is not finished: " + taken + " of " + required + " frames");
		this.required = required;
		this.taken = taken;
	}

	public int required() {
		return required;
	}

	public int taken() {
		return taken;
	}
}
