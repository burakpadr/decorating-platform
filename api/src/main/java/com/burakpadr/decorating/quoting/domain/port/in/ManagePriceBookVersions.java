package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import java.util.List;
import java.util.UUID;

/**
 * The operator's price book management use case (§7, workflow §6).
 *
 * <p>Three operations, and the shape of them is the guarantee: a version is created by copying an
 * existing one, edited while it is inactive, and then activated. There is no operation that changes a
 * version's figures once it has priced anything, which is what lets a customer turn up with a
 * two-week-old quote and be told exactly how it was arrived at ({@code docs/decisions/0010}).
 */
public interface ManagePriceBookVersions {

	List<PriceBookSummary> list();

	/**
	 * Copies a version whole — items, modifiers, room types and districts — under a new code. The copy
	 * starts inactive: a list nobody has looked at must not be pricing quotes.
	 */
	PriceBookSummary createVersionFrom(UUID sourceId, String versionCode);

	/** Makes a version the one quotes are priced against, switching off whichever was. */
	PriceBookSummary activate(UUID id);
}
