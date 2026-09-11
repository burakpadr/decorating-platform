package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * The figures of a price book version that are not item costs (§4.5, §5.3–5.8, BOYA-20a).
 *
 * <p>Everything here multiplies <em>every</em> square metre of every quote, which is what makes the
 * bounds worth having: a mistyped coefficient does not produce one wrong line, it produces a wrong
 * price list whose only symptom is that the prices are strange. The bounds are wide on purpose —
 * they catch a decimal point in the wrong place, not a decision somebody made deliberately.
 *
 * <p>Crew size and crew day cost travel <b>together</b> rather than as two separate edits. The two
 * are halves of one figure, the cost of a person for a day: change the size alone and every item's
 * labour is silently rewritten, because ADR 0016 derives labour from
 * {@code crewDayCost / (crewSize × hours × 60)}. A record with both fields cannot be half-applied.
 *
 * <p>What is <em>not</em> here: the engineering constants of §5.3–5.7 (room type weights, the
 * corridor's perimeter factor, opening areas, the day rounding tolerance, the base band ratio). The
 * setup wizard ships those as structural defaults and does not ask for them (BOYA-70), so exposing
 * them for editing would invite a change nobody could calibrate against anything.
 */
public record PriceBookCoefficients(
		BigDecimal ceilingHeightM,
		BigDecimal grossToNetRatio,
		BigDecimal stage1OpeningRatio,
		int crewSize,
		BigDecimal crewHoursPerDay,
		BigDecimal crewDayCost,
		BigDecimal marginRatio,
		BigDecimal marginAlertThreshold,
		BigDecimal labourVatRate,
		BigDecimal materialVatRate) {

	public PriceBookCoefficients {
		between(ceilingHeightM, "2.00", "4.00", "tavan yüksekliği");
		between(grossToNetRatio, "0.50", "1.00", "brüt-net oranı");
		between(stage1OpeningRatio, "0.00", "0.50", "Aşama 1 boşluk oranı");
		if (crewSize < 1 || crewSize > 20) {
			throw new IllegalArgumentException("ekip büyüklüğü 1 ile 20 kişi arasında olmalı: " + crewSize);
		}
		between(crewHoursPerDay, "1.00", "16.00", "günlük çalışma saati");
		above(crewDayCost, "günlük ekip maliyeti");
		between(marginRatio, "0.0001", "0.9000", "kâr marjı");
		between(marginAlertThreshold, "0.0001", "0.9000", "marj uyarı eşiği");
		if (marginAlertThreshold.compareTo(marginRatio) > 0) {
			// Above the target every single job alerts, and an alert that always fires is an alert
			// nobody reads.
			throw new IllegalArgumentException("marj uyarı eşiği hedef kâr marjını geçemez: "
					+ marginAlertThreshold + " > " + marginRatio);
		}
		between(labourVatRate, "0.0001", "0.9999", "işçilik KDV oranı");
		between(materialVatRate, "0.0001", "0.9999", "malzeme KDV oranı");
	}

	private static void between(BigDecimal value, String low, String high, String name) {
		if (value == null) {
			throw new IllegalArgumentException(name + " girilmeli");
		}
		if (value.compareTo(new BigDecimal(low)) < 0 || value.compareTo(new BigDecimal(high)) > 0) {
			throw new IllegalArgumentException(
					name + " " + low + " ile " + high + " arasında olmalı: " + value);
		}
	}

	private static void above(BigDecimal value, String name) {
		if (value == null || value.signum() <= 0) {
			throw new IllegalArgumentException(name + " sıfırdan büyük olmalı: " + value);
		}
	}
}
