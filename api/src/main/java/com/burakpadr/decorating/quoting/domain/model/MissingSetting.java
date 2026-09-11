package com.burakpadr.decorating.quoting.domain.model;

/**
 * One thing setup still has to supply before this install can quote (BOYA-69, workflow §12).
 *
 * <p>An enum rather than a sentence because two screens read it: the panel decides whether to open
 * the wizard (BOYA-70), and the customer end has to recognise the refusal without matching on Turkish
 * prose — the same reason {@code DistrictNotServed} carries a code (workflow §8).
 *
 * <p>They are the money and the locale. The engineering coefficients of §5.3–5.7 ship as structural
 * defaults and are not asked for, so nothing here names one.
 */
public enum MissingSetting {

	/** No active price book version at all: nothing to price against (§4.5). */
	PRICE_BOOK,

	/** No district switched on, so there is nowhere the business says it works (workflow §7). */
	SERVICE_DISTRICTS,

	/** Labour VAT. §16 wants an accountant for this one and the next; they are taxed apart. */
	LABOUR_VAT_RATE,

	/** Material VAT. */
	MATERIAL_VAT_RATE,

	/** The target margin of §5.8 step 12. At zero the engine quotes the job at cost. */
	MARGIN_RATIO
}
