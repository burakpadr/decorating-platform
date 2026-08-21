package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * One half of a quote — labour, or material — carried through the same steps as the whole.
 *
 * <p>The engine has always kept the two apart internally: §6's modifiers apply to one half or both,
 * and §5.8 taxes each at its own rate, so a blended figure could not reproduce either. What this record
 * does is stop that split from being thrown away at the end.
 *
 * <p>It exists because the business quotes labour on its own — the customer buys the paint — so
 * "işçilik dahil mi" is a real question about a real quote and not a display preference. Answering it in
 * the client would mean re-applying margin and VAT there, which is the same mistake as
 * {@code docs/decisions/0016}: two copies of one calculation, free to disagree.
 *
 * <p>Each half is rounded from its own chain, so each is what that half is worth. The two therefore add
 * back to the whole to within a kuruş rather than exactly — on §5.10 only the VAT line is a kuruş out.
 * The alternative, deriving material by subtracting labour from the whole, buys exact addition by making
 * the material half move whenever labour does, and a paint cost that changes because the home is
 * furnished is worse than a kuruş that does not add up. Nothing shows a half beside the whole anyway:
 * the operator picks a scope, and the figures shown are that scope's.
 */
public record QuotePortion(
		BigDecimal cost,
		BigDecimal subtotalExVat,
		BigDecimal vatAmount,
		BigDecimal total) {}
