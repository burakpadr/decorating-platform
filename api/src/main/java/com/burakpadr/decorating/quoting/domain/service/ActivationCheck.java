package com.burakpadr.decorating.quoting.domain.service;

import com.burakpadr.decorating.quoting.domain.model.ActivationProblem;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookItem;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Whether a version is fit to price with (BOYA-20a, ADR 0016).
 *
 * <p>Everything here is a defect the engine would otherwise meet at pricing time, against a real
 * customer: a code it looks up and cannot find, or a labour figure that contradicts the same book's
 * own crew rate. The first throws, which is loud. The second does not — it produces a plausible
 * number, and the only symptom is that the prices are strange. That is the one worth a gate.
 *
 * <p>It runs at <b>activation</b> rather than on every write, because that is the moment a version
 * stops being a draft somebody is editing and starts answering customers. An inactive version is
 * allowed to be half-finished; the wizard and the panel both build one field at a time.
 *
 * <p>Pure, and for the engine's reason: this is the gate in front of every price the version would
 * produce, so every branch has to be drivable from a test with no database.
 */
public class ActivationCheck {

	/** Minutes are PERSON-minutes (§5.8), so the crew rate divides by crew size as well as by hours. */
	private static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");

	/** Empty means fit to activate. */
	public List<ActivationProblem> check(PriceBook book) {
		List<ActivationProblem> problems = new ArrayList<>();

		// The money first, because a version at zero passes the labour rule trivially — zero minutes'
		// worth of a zero crew rate is zero, and every item agrees with it.
		money(problems, "crewDayCost", book.crewDayCost());
		money(problems, "marginRatio", book.marginRatio());
		money(problems, "labourVatRate", book.labourVatRate());
		money(problems, "materialVatRate", book.materialVatRate());

		for (ItemCode code : ItemCode.values()) {
			PriceBookItem item = book.items().get(code);
			if (item == null) {
				problems.add(new ActivationProblem(ActivationProblem.Kind.MISSING_ITEM, code.name(),
						"§5.6 prices this code on every quote and this version has no row for it"));
				continue;
			}
			BigDecimal derived = derivedLabourCost(book, item.labourMinutes());
			if (derived.compareTo(item.labourCost()) != 0) {
				problems.add(new ActivationProblem(ActivationProblem.Kind.LABOUR_DOES_NOT_RECONCILE,
						code.name(),
						"labour is " + item.labourCost() + " TL but " + item.labourMinutes()
								+ " person-minutes at this version's crew rate come to " + derived + " TL"));
			}
		}

		for (RoomType type : RoomType.values()) {
			if (!book.roomTypes().containsKey(type)) {
				problems.add(new ActivationProblem(ActivationProblem.Kind.MISSING_ROOM_TYPE, type.name(),
						"§5.3 weighs the home's area by room type and this version has no row for it"));
			}
		}
		return List.copyOf(problems);
	}

	private static void money(List<ActivationProblem> problems, String field, BigDecimal value) {
		if (value == null || value.signum() <= 0) {
			problems.add(new ActivationProblem(ActivationProblem.Kind.MONEY_NOT_ENTERED, field,
					"bu sürümde " + field + " girilmemiş (" + value + "), yani her iş maliyetine "
							+ "fiyatlanır"));
		}
	}

	/**
	 * What these minutes cost at the version's crew rate, to the kuruş.
	 *
	 * <p><b>Multiply first, divide last, round once.</b> This has to be the same arithmetic the database
	 * performs when it derives the column, or the two disagree on every figure that lands exactly on
	 * half a kuruş — and the live book has two of them: PRIMER's three minutes come to 15.625 and
	 * CORNICE_CUTTING's forty-five to 234.375. Working out a per-minute rate first and multiplying by it
	 * turns both into 15.6249… and refuses a version that is perfectly consistent. This test caught that
	 * on the way in.
	 */
	private static BigDecimal derivedLabourCost(PriceBook book, BigDecimal labourMinutes) {
		return labourMinutes.multiply(book.crewDayCost())
				.divide(BigDecimal.valueOf(book.crewSize())
						.multiply(book.crewHoursPerDay())
						.multiply(MINUTES_PER_HOUR), 2, RoundingMode.HALF_UP);
	}
}
