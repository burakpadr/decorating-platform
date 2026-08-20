package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * A modifier of §5.7.
 *
 * <p>An empty {@code scopeItems} means every item, matching the migration's {@code null} column.
 */
public record PriceModifier(
		ModifierCode code, BigDecimal factor, ModifierTarget target, Set<ItemCode> scopeItems) {

	public PriceModifier {
		scopeItems = Set.copyOf(scopeItems);
	}

	public boolean covers(ItemCode item) {
		return scopeItems.isEmpty() || scopeItems.contains(item);
	}

	public boolean affectsLabour() {
		return target == ModifierTarget.LABOUR || target == ModifierTarget.BOTH;
	}

	public boolean affectsMaterial() {
		return target == ModifierTarget.MATERIAL || target == ModifierTarget.BOTH;
	}
}
