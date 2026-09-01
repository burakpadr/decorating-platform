package com.burakpadr.decorating.quoting.domain.model;

/**
 * §11's send limits: one message a minute to a number, five a day, ten an hour from an address.
 *
 * <p>"Strict, because every SMS costs money and this is the most attackable endpoint." The phone is
 * the primary limit and the address is a backstop, not the other way round: Turkish carriers put
 * thousands of subscribers behind one CGNAT address, so a strict address limit blocks real customers.
 */
public class TooManyOtpRequests extends RuntimeException {

	private final java.time.Duration retryAfter;

	public TooManyOtpRequests(String message, java.time.Duration retryAfter) {
		super(message);
		this.retryAfter = retryAfter;
	}

	/** How long to wait, so the screen can say it rather than leaving the customer pressing. */
	public java.time.Duration retryAfter() {
		return retryAfter;
	}
}
