package com.burakpadr.decorating.quoting.domain.model;

/**
 * Consent was offered before there was anything to consent to (workflow §2.2 then §2.3).
 *
 * <p>§2.3 is the screen after the room list is agreed, and the order is not decoration: the notice
 * describes what will happen to photographs of the areas just settled on. A grant recorded against a
 * request that is still a draft would be a grant about nothing, and it would then sit in the record
 * looking exactly like a real one.
 *
 * <p>Its own type so the endpoint does not answer with the state machine's sentence about answers no
 * longer being changeable, which is true of a different refusal entirely.
 */
public class ConsentOutOfOrder extends RuntimeException {

	public ConsentOutOfOrder(String message) {
		super(message);
	}
}
