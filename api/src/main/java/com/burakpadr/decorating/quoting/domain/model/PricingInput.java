package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * The engine's input contract (§5.1).
 *
 * <p>{@code netArea} is always net: where the customer gave gross, the caller applies
 * {@code grossToNetRatio} and sets {@code areaWasGross}, which §5.9 turns into a wider band. The
 * engine does not convert, so it cannot convert twice.
 *
 * <p>{@code doorCountEstimated} is not in §5.1's record but §5.9 prices it (+0.03 to the band), and
 * a band term whose input cannot be expressed is a band term that never fires. Carried here as the
 * narrowest resolution; the two sections disagree and one of them is wrong.
 */
public record PricingInput(
		String districtCode,
		BigDecimal netArea,
		boolean areaWasGross,
		List<RoomInput> rooms,
		Furnishing furnishing,
		int doorCount,
		boolean doorColourChange,
		boolean doorCountEstimated,
		boolean hasElevator,
		boolean rush,
		PricingSource source) {

	public PricingInput {
		rooms = List.copyOf(rooms);
	}

	/**
	 * The input a customer-facing stage builds from the answers it collected (§5.1).
	 *
	 * <p>§5.1 is explicit that stage 1 and stage 2 build the same object and that the engine must not
	 * know which produced it. Here is where that stops being a sentence: everything a customer declared
	 * is read from one place, the three unasked questions come from one set of constants, and the only
	 * things the two stages choose for themselves are the rooms — a declaration on one side, findings on
	 * the other — and the {@code source} the two sections that are allowed to branch read (§5.5's
	 * opening deduction, §5.9's band).
	 *
	 * <p>{@code doorCount} stays the customer's declared total even in stage 2, where the analysis has
	 * counted doors of its own. §6 is explicit: detection flags a disagreement for the operator and does
	 * not silently override a declaration (BOYA-52). The counted doors are per room and they do a
	 * different job — deducting openings from the wall area (§5.5).
	 */
	public static PricingInput declaredBy(StageOneAnswers answers, BigDecimal netArea,
			boolean areaWasGross, List<RoomInput> rooms, PricingSource source) {
		return new PricingInput(
				answers.districtCode(),
				netArea,
				areaWasGross,
				rooms,
				answers.furnishing(),
				answers.doorCount() == null ? 0 : answers.doorCount(),
				Boolean.TRUE.equals(answers.doorColourChange()),
				StageOneAnswers.UNASKED_DOOR_COUNT_ESTIMATED,
				StageOneAnswers.UNASKED_HAS_ELEVATOR,
				StageOneAnswers.UNASKED_RUSH,
				source);
	}
}
