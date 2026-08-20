package com.burakpadr.decorating.quoting.domain.model;

/**
 * Which half of a cost a bulk increase moves (§7, workflow §6).
 *
 * <p>Labour and material are raised separately because they change at different rhythms: paint goes up
 * with the exchange rate, wages go up with the minimum wage. Raising both because one moved is how a
 * price list drifts away from its market.
 */
public enum IncreaseTarget {
	LABOUR,
	MATERIAL,
	ALL;

	public boolean raisesLabour() {
		return this == LABOUR || this == ALL;
	}

	public boolean raisesMaterial() {
		return this == MATERIAL || this == ALL;
	}
}
