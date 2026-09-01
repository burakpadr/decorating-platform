package com.burakpadr.decorating.quoting.domain.model;

/**
 * The code did not verify (workflow §3.1, §11).
 *
 * <p>One type for every reason it did not: wrong digits, expired, superseded by a newer code, already
 * used, or never sent at all. A caller that could tell those apart could learn whether a code exists
 * and how old it is, which is most of what a guesser wants to know — and the customer's next move is
 * the same in all five cases.
 */
public class OtpRefused extends RuntimeException {

	public OtpRefused(String message) {
		super(message);
	}
}
