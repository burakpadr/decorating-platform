package com.burakpadr.decorating.quoting.domain.model;

/**
 * This code has been guessed at too many times (§11: "lock after 5").
 *
 * <p>Its own type because the answer is different: the code is finished and no number of further
 * attempts will help, so the screen has to offer a new one rather than another try. Locking the code
 * rather than the phone is deliberate — locking the number would let anybody who knows it lock a
 * stranger out of their own quote.
 */
public class OtpLocked extends RuntimeException {

	public OtpLocked(String message) {
		super(message);
	}
}
