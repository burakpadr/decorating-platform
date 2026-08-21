package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.Set;

/**
 * What the customer has answered so far (§2.1's eight questions, §4.2's stage 1 columns).
 *
 * <p>Its own record rather than nine more components on {@link QuoteRequest}: the answers accumulate
 * across three screens and the state machine does not care what they say, so the two change for
 * different reasons and at different times.
 *
 * <p><b>Every field is boxed, and that is the design.</b> Null means "not answered yet", which is a
 * different thing from every value any of these fields can hold — and for two of them the difference is
 * money. A primitive {@code int doorCount} makes "no doors to paint" indistinguishable from silence, and
 * a primitive {@code boolean} makes "no colour change" the same as not having reached that screen. The
 * one customer who answers zero would be quietly given whatever the previous screen held.
 */
public record StageOneAnswers(
		String districtCode,
		BigDecimal areaInput,
		AreaBasis areaBasis,
		Layout layout,
		QuoteScope scope,
		Furnishing furnishing,
		Integer doorCount,
		Boolean doorColourChange,
		WallCondition wallCondition,
		/**
		 * Which areas the customer chose, when {@code scope} is {@code SELECTED_ROOMS}. Null until the
		 * question is answered — an empty selection is a different answer and §5.1 refuses it, because a
		 * quote for no rooms is not a cheap quote.
		 */
		Set<RoomType> selectedRooms) {

	private static final StageOneAnswers EMPTY =
			new StageOneAnswers(null, null, null, null, null, null, null, null, null, null);

	public StageOneAnswers {
		selectedRooms = selectedRooms == null ? null : Set.copyOf(selectedRooms);
	}

	/** A request that has been created and answered nothing. */
	public static StageOneAnswers empty() {
		return EMPTY;
	}

	/**
	 * These answers with the given ones written over them, field by field, absent meaning unchanged.
	 *
	 * <p>Exhaustive on purpose and tested field by field. A merge that misses a field is a customer
	 * answering a question twice, and a merge that overwrites an answer with nothing is worse: the
	 * estimate is computed from whatever survived and the gap is invisible.
	 *
	 * <p>There is no way to un-answer a question. The form only ever adds and corrects — going back and
	 * choosing differently is a value, not an absence — so "clear this field" has no caller and would
	 * only be a second meaning for null.
	 */
	public StageOneAnswers mergedWith(StageOneAnswers patch) {
		if (patch == null) {
			return this;
		}
		return new StageOneAnswers(
				pick(patch.districtCode, districtCode),
				pick(patch.areaInput, areaInput),
				pick(patch.areaBasis, areaBasis),
				pick(patch.layout, layout),
				pick(patch.scope, scope),
				pick(patch.furnishing, furnishing),
				pick(patch.doorCount, doorCount),
				pick(patch.doorColourChange, doorColourChange),
				pick(patch.wallCondition, wallCondition),
				pick(patch.selectedRooms, selectedRooms));
	}

	/**
	 * Whether the engine could price this (§5.1's required input).
	 *
	 * <p>Asked here so the estimate endpoint refuses rather than guesses. A missing wall condition is not
	 * a cheaper job to quote, it is a different one — §5.6 turns it into filler quantities — and a
	 * default would be the engine answering a question nobody asked.
	 */
	public boolean isPriceable() {
		if (districtCode == null || areaInput == null || areaBasis == null || layout == null
				|| scope == null || furnishing == null || wallCondition == null) {
			return false;
		}
		// A selection of nothing is not a smaller job, it is no job — and WHOLE_HOME needs no selection
		// at all, because the layout derives the list (§2.1).
		return scope != QuoteScope.SELECTED_ROOMS
				|| (selectedRooms != null && !selectedRooms.isEmpty());
	}

	private static <T> T pick(T patched, T current) {
		return patched != null ? patched : current;
	}
}
