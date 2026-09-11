package com.burakpadr.decorating.quoting.domain.model;

/**
 * Asked for a price by an install that has not been set up (BOYA-69).
 *
 * <p>Its own type, and not an {@link IllegalStateException}: this is not a bug and not a mistake the
 * caller made, and both ends have to answer it with a screen of their own — the panel opens the setup
 * wizard (BOYA-70), the customer end says the service is not ready. An exception the handlers cannot
 * tell apart from a programming error would get a 500 and neither screen would happen.
 *
 * <p>It carries the whole {@link SetupStatus} rather than a flag, so the operator's refusal can say
 * what to enter without asking a second time. What reaches a <em>customer</em> is only that the
 * service is not ready; the list is about this install's insides.
 */
public class SetupIncomplete extends RuntimeException {

	private final transient SetupStatus status;

	public SetupIncomplete(SetupStatus status) {
		super("this install cannot price anything yet — still to be entered: " + status.missing());
		this.status = status;
	}

	public SetupStatus status() {
		return status;
	}
}
