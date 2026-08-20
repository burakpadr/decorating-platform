package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the engine returns (§5.2, §5.8, §5.9).
 *
 * <p>{@code totalCost} and the margin are internal figures — §1's rule keeps them off customer
 * DTOs, which is the adapter's job and not this record's.
 *
 * <p>{@code bandLow} and {@code bandHigh} sit symmetrically around {@code total} by construction.
 * Low confidence widens the band and never shifts the midpoint: painting surprises are
 * one-directional, so pulling an uncertain estimate toward an average underquotes systematically.
 */
public record PricedQuote(
		String priceBookVersion,
		List<QuoteLine> lines,
		BigDecimal totalMinutes,
		int billableDays,
		BigDecimal minimumCost,
		boolean minimumBinding,
		BigDecimal totalCost,
		BigDecimal subtotalExVat,
		BigDecimal vatAmount,
		BigDecimal total,
		BigDecimal bandRatio,
		BigDecimal bandLow,
		BigDecimal bandHigh) {

	public PricedQuote {
		lines = List.copyOf(lines);
	}

	public QuoteLine line(ItemCode code) {
		return lines.stream()
				.filter(l -> l.code() == code)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("no line for " + code));
	}

	public boolean hasLine(ItemCode code) {
		return lines.stream().anyMatch(l -> l.code() == code);
	}
}
