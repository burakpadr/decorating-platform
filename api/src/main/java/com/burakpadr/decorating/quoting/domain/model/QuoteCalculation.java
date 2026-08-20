package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * What the internal tool answers with: the price, and everything it assumed to get there.
 *
 * <p>The assumptions travel with the figure on purpose. The business is comparing this against a price
 * it arrived at itself, and "52.520" alone cannot be argued with — "seven areas, 92 m² net, 221 m² of
 * wall, priced against REAL-2026-01" can. A number nobody can take apart is a number nobody will
 * trust, and trust in this figure is the whole point of increment 1.
 */
public record QuoteCalculation(
		PricedQuote quote,
		RoomList rooms,
		BigDecimal netArea,
		boolean areaWasGross,
		String priceBookVersion) {}
