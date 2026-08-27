package com.burakpadr.decorating.quoting.domain.model;

/**
 * The customer agreed to a notice that is no longer the current one (§12).
 *
 * <p>Its own type rather than a validation failure, because the answer is a screen and not a fix: the
 * text changed between the page rendering and the tick, so the only honest thing to do is show the new
 * words and ask again.
 *
 * <p>The alternative — stamping the current version onto a grant given against different words — would
 * produce precisely the record versioning exists to prevent: a {@code text_version} that does not say
 * what the customer read.
 */
public class ConsentNoticeChanged extends RuntimeException {

	private final String current;

	public ConsentNoticeChanged(String read, String current) {
		super("the notice has changed since it was read: " + read + " is no longer " + current);
		this.current = current;
	}

	/** The version the customer should be shown instead. */
	public String current() {
		return current;
	}
}
