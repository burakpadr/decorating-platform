package com.burakpadr.decorating.quoting.domain.model;

/**
 * §3.1 has not happened yet (§3's other guard on the submit arrow).
 *
 * <p>Its own type because it is not a mistake and not a conflict — it is the next step. The customer
 * is sent to the verification screen, which is where §3.2 says they were going anyway.
 */
public class PhoneNotVerified extends RuntimeException {

	public PhoneNotVerified(String message) {
		super(message);
	}
}
