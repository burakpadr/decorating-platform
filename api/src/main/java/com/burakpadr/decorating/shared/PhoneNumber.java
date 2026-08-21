package com.burakpadr.decorating.shared;

import java.util.regex.Pattern;

/**
 * A Turkish mobile number, in one form (§2's shared package).
 *
 * <p>Normalisation is not tidiness. {@code customer.phone} is UNIQUE and §4.1 resolves a returning
 * customer by looking their number up, so the same person typing {@code 0555…} on Monday and
 * {@code +90 555…} on Friday has to land on the same row. Two rows for one phone is a repeat customer
 * the business cannot see. §4.6's rate limit keys off this string too — its own comment reads
 * {@code "phone:+9053…"} — so a number that normalises two ways is a limit that counts to twice.
 *
 * <p>Only mobiles. A landline is a real number that no SMS reaches, and accepting one means a customer
 * waiting for a message that was never deliverable, with nothing anywhere saying why.
 */
public final class PhoneNumber {

	/** After stripping everything but digits and a leading plus. */
	private static final Pattern TURKISH_MOBILE = Pattern.compile("^(?:\\+?90|0)?(5\\d{9})$");

	private static final Pattern NOT_A_NUMBER = Pattern.compile("[^0-9+]");

	private final String e164;

	private PhoneNumber(String e164) {
		this.e164 = e164;
	}

	public static PhoneNumber of(String typed) {
		if (typed == null || typed.isBlank()) {
			throw new IllegalArgumentException("a phone number is required");
		}
		String digits = NOT_A_NUMBER.matcher(typed.strip()).replaceAll("");
		var match = TURKISH_MOBILE.matcher(digits);
		if (!match.matches()) {
			// The rejected value is not echoed: an error message is a log line waiting to happen, and the
			// caller already knows what it sent.
			throw new IllegalArgumentException(
					"not a Turkish mobile number: it must be a 5xx number, with or without +90");
		}
		return new PhoneNumber("+90" + match.group(1));
	}

	/** The stored and transmitted form. */
	public String e164() {
		return e164;
	}

	/**
	 * Enough of the number to recognise, not enough to dial.
	 *
	 * <p>The {@code notification} row keeps the real one, which is how a customer who says "no SMS
	 * arrived" gets an answer. Everything that gets read by a human in passing — logs, error messages,
	 * an operator list — gets this.
	 */
	public String masked() {
		return "+90 " + e164.substring(3, 6) + " *** ** " + e164.substring(11);
	}

	@Override
	public String toString() {
		// Masked on purpose: toString is what ends up in a log line nobody wrote deliberately.
		return masked();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof PhoneNumber phone && e164.equals(phone.e164);
	}

	@Override
	public int hashCode() {
		return e164.hashCode();
	}
}
