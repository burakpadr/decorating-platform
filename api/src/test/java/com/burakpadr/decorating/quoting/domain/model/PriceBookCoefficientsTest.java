package com.burakpadr.decorating.quoting.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bounds on a price book's coefficients (BOYA-20a).
 *
 * <p>These are the figures §5.3–5.5 apply to <em>every</em> square metre of every quote, so a
 * mistyped one does not produce a wrong line — it produces a wrong price list, and the only symptom
 * is that the prices are strange. The bounds are deliberately wide: they catch the decimal point in
 * the wrong place, not a business decision somebody made on purpose.
 */
class PriceBookCoefficientsTest {

	@Test
	@DisplayName("the figures this installation runs on are accepted")
	void theLiveFiguresAreWithinBounds() {
		assertThatCode(PriceBookCoefficientsTest::valid).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("a ceiling height is between 2 and 4 metres, so 27 metres is a missing comma")
	void ceilingHeightIsBounded() {
		assertThatThrownBy(() -> with(b -> b.ceilingHeight(new BigDecimal("27.00"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("tavan yüksekliği");
		assertThatThrownBy(() -> with(b -> b.ceilingHeight(new BigDecimal("1.50"))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("the gross-to-net ratio cannot exceed 1: net area is never larger than gross")
	void grossToNetIsBounded() {
		assertThatThrownBy(() -> with(b -> b.grossToNet(new BigDecimal("1.20"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("brüt-net");
		assertThatThrownBy(() -> with(b -> b.grossToNet(new BigDecimal("0.20"))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("a crew has at least one person, and a person works a plausible day")
	void crewFiguresAreBounded() {
		assertThatThrownBy(() -> with(b -> b.crewSize(0)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ekip");
		assertThatThrownBy(() -> with(b -> b.crewHours(new BigDecimal("30.00"))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> with(b -> b.crewDayCost(BigDecimal.ZERO)))
				.isInstanceOf(IllegalArgumentException.class)
				.as("a crew that costs nothing prices every job at its materials");
	}

	@Test
	@DisplayName("a margin of zero prices the job at cost, and 0.95 is a mistyped percentage")
	void marginIsBounded() {
		assertThatThrownBy(() -> with(b -> b.margin(BigDecimal.ZERO)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("kâr marjı");
		assertThatThrownBy(() -> with(b -> b.margin(new BigDecimal("0.9500"))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("the alert threshold sits at or below the target — above it, every job alerts")
	void theAlertThresholdCannotExceedTheTarget() {
		assertThatThrownBy(() -> with(b -> b.alertThreshold(new BigDecimal("0.4000"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("uyarı eşiği");
		assertThatCode(() -> with(b -> b.alertThreshold(new BigDecimal("0.3000"))))
				.as("equal is allowed: alert as soon as the target is missed at all")
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("a VAT rate is a rate, not a percentage: 20 is 2000%")
	void vatRatesAreBounded() {
		assertThatThrownBy(() -> with(b -> b.labourVat(new BigDecimal("20.0000"))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("KDV");
		assertThatThrownBy(() -> with(b -> b.materialVat(BigDecimal.ZERO))).as(
						"zero is how an unconfigured install reads (BOYA-69), so it cannot also be a choice")
				.isInstanceOf(IllegalArgumentException.class);
	}

	// =============================================================================================

	/** REAL-2026-03's own figures: two painters at 5,000 TL a day, 30% margin, 20% / 10% VAT. */
	private static PriceBookCoefficients valid() {
		return new Builder().build();
	}

	private static void with(java.util.function.Consumer<Builder> change) {
		Builder builder = new Builder();
		change.accept(builder);
		builder.build();
	}

	private static final class Builder {
		private BigDecimal ceilingHeight = new BigDecimal("2.70");
		private BigDecimal grossToNet = new BigDecimal("0.8200");
		private BigDecimal openingRatio = new BigDecimal("0.1200");
		private int crewSize = 2;
		private BigDecimal crewHours = new BigDecimal("8.00");
		private BigDecimal crewDayCost = new BigDecimal("5000.00");
		private BigDecimal margin = new BigDecimal("0.3000");
		private BigDecimal alertThreshold = new BigDecimal("0.2000");
		private BigDecimal labourVat = new BigDecimal("0.2000");
		private BigDecimal materialVat = new BigDecimal("0.1000");

		Builder ceilingHeight(BigDecimal v) { this.ceilingHeight = v; return this; }
		Builder grossToNet(BigDecimal v) { this.grossToNet = v; return this; }
		Builder crewSize(int v) { this.crewSize = v; return this; }
		Builder crewHours(BigDecimal v) { this.crewHours = v; return this; }
		Builder crewDayCost(BigDecimal v) { this.crewDayCost = v; return this; }
		Builder margin(BigDecimal v) { this.margin = v; return this; }
		Builder alertThreshold(BigDecimal v) { this.alertThreshold = v; return this; }
		Builder labourVat(BigDecimal v) { this.labourVat = v; return this; }
		Builder materialVat(BigDecimal v) { this.materialVat = v; return this; }

		PriceBookCoefficients build() {
			return new PriceBookCoefficients(ceilingHeight, grossToNet, openingRatio, crewSize,
					crewHours, crewDayCost, margin, alertThreshold, labourVat, materialVat);
		}
	}
}
