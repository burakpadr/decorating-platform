package com.burakpadr.decorating.quoting.domain.model;

/**
 * A photograph was asked for before the data notice was agreed to (workflow §2.3 then §2.4).
 *
 * <p>Enforced on the server because §2.3's consent is otherwise decorative: the screen can be skipped
 * by anyone who knows the next URL, and the frames would arrive with no record of permission to hold
 * them. The screen is the polite path; this is the rule.
 *
 * <p>Also raised after a refusal. Withdrawing consent has to stop the next frame, or "no" would mean
 * nothing for the rest of the session.
 */
public class ConsentMissing extends RuntimeException {

	public ConsentMissing(String message) {
		super(message);
	}
}
