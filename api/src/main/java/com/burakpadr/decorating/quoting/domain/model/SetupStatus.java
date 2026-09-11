package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Whether this install has been set up enough to quote, and what it still owes (BOYA-69).
 *
 * <p>The project is open source, so the first install is a stranger's: their crew, their city, their
 * VAT rates. The migrations therefore ship the schema and no price book (decision 0024), and this is
 * the other half of that — the default has to fail on the safe side. An install that refuses to quote
 * is an annoyance; an install that quotes from figures nobody entered issues an invoice, and the only
 * symptom is that the prices are strange.
 *
 * <p>Pure, and deliberately: it is the gate in front of every price a fresh clone could produce, so
 * every branch has to be drivable from a test with no database — the reason {@code PricingEngine} and
 * {@code ConfidenceEvaluator} are pure too.
 *
 * <p><b>What it does not answer.</b> Whether the figures are <em>this business's</em> figures. No
 * column can carry that: a margin of 0.30 is both a real decision and the schema's own DEFAULT, and
 * §5.11's costs are market-derived placeholders that look exactly like real ones. That question is
 * Phase 0's (BOYA-1, BOYA-8) and cannot be turned into a check here without inventing a false one.
 */
public record SetupStatus(List<MissingSetting> missing) {

	public SetupStatus {
		missing = List.copyOf(missing);
	}

	/**
	 * What is left to enter, read off the active version — or off its absence.
	 *
	 * <p>With no version, all five are reported rather than {@code PRICE_BOOK} alone. The list is
	 * "what setup still has to supply", which is what the wizard asks for either way; reporting one
	 * entry would leave every client to infer the other four from it, and one of them eventually
	 * would not.
	 */
	public static SetupStatus of(Optional<PriceBook> active) {
		if (active.isEmpty()) {
			return new SetupStatus(List.of(MissingSetting.values()));
		}
		PriceBook book = active.get();
		List<MissingSetting> missing = new ArrayList<>();
		if (book.servedDistricts().isEmpty()) {
			// Switched off counts as absent: §4.5 keeps a closed district's row for the quotes it
			// priced, so presence is not the question — being served is.
			missing.add(MissingSetting.SERVICE_DISTRICTS);
		}
		if (isUnset(book.labourVatRate())) {
			missing.add(MissingSetting.LABOUR_VAT_RATE);
		}
		if (isUnset(book.materialVatRate())) {
			missing.add(MissingSetting.MATERIAL_VAT_RATE);
		}
		if (isUnset(book.marginRatio())) {
			missing.add(MissingSetting.MARGIN_RATIO);
		}
		return new SetupStatus(missing);
	}

	public boolean complete() {
		return missing.isEmpty();
	}

	/**
	 * Zero reads as "nobody entered it". It is a weak test and an honest one: the alternative is a
	 * nullable column, and making a rate nullable to record that it was never typed would let every
	 * pricing path meet a null it has no answer for.
	 */
	private static boolean isUnset(BigDecimal rate) {
		return rate == null || rate.signum() <= 0;
	}
}
