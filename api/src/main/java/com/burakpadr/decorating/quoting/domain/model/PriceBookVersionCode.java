package com.burakpadr.decorating.quoting.domain.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Naming the version a bulk increase produces.
 *
 * <p>§7's bulk-increase body carries a target and a percent and no name, so the name is derived from
 * the source: {@code REAL-2026-01} becomes {@code REAL-2026-02}. The operator does this every quarter
 * and should not have to invent a name — but the name still has to be readable months later, when a
 * customer turns up holding a quote priced against one of them.
 */
public final class PriceBookVersionCode {

	/** The longest trailing run of digits, if the code ends in one. */
	private static final Pattern TRAILING_NUMBER = Pattern.compile("^(.*?)(\\d+)$");

	/** {@code price_book.version_code} is varchar(32). */
	private static final int MAX_LENGTH = 32;

	private PriceBookVersionCode() {}

	public static String next(String code) {
		Matcher matcher = TRAILING_NUMBER.matcher(code);
		String next = matcher.matches()
				? matcher.group(1) + advance(matcher.group(2))
				: code + "-2";

		if (next.length() > MAX_LENGTH) {
			throw new IllegalArgumentException(
					"the next code after " + code + " does not fit in version_code(32)");
		}
		return next;
	}

	/** Keeps the width it was given — 01 becomes 02, not 2 — and grows only when it has to. */
	private static String advance(String digits) {
		String advanced = String.valueOf(Long.parseLong(digits) + 1);
		return advanced.length() >= digits.length()
				? advanced
				: "0".repeat(digits.length() - advanced.length()) + advanced;
	}
}
