package com.burakpadr.decorating.quoting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.quoting.domain.PriceBookFixture;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What an install still owes before it can quote anybody (BOYA-69).
 *
 * <p>Pure, and for the same reason {@code ConfidenceEvaluator} is: this is the rule that decides
 * whether a stranger's clone answers a real customer with figures nobody entered, so every branch has
 * to be drivable without a database.
 *
 * <p>It answers "is there anything to price with", not "are these the right figures". No column can
 * carry the second question — a margin of 0.30 is both a plausible business decision and the schema's
 * own DEFAULT — so the placeholder problem stays where it belongs, in Phase 0 (BOYA-1, BOYA-8).
 */
class SetupStatusTest {

	@Test
	@DisplayName("with no active version, everything setup has to supply is still missing")
	void aFreshInstallOwesEverything() {
		SetupStatus status = SetupStatus.of(Optional.empty());

		assertThat(status.complete()).isFalse();
		assertThat(status.missing())
				.as("the wizard asks for all of it, so listing only PRICE_BOOK would make the client "
						+ "infer the other four from it")
				.containsExactlyInAnyOrder(
						MissingSetting.PRICE_BOOK,
						MissingSetting.SERVICE_DISTRICTS,
						MissingSetting.LABOUR_VAT_RATE,
						MissingSetting.MATERIAL_VAT_RATE,
						MissingSetting.MARGIN_RATIO);
	}

	@Test
	@DisplayName("an active version with districts, both VAT rates and a margin is set up")
	void aConfiguredInstallIsComplete() {
		SetupStatus status = SetupStatus.of(Optional.of(PriceBookFixture.seed()));

		assertThat(status.complete()).isTrue();
		assertThat(status.missing()).isEmpty();
	}

	@Test
	@DisplayName("a version nobody switched a district on for cannot serve anyone")
	void districtsAllSwitchedOffCountAsMissing() {
		PriceBook noneServed = withDistricts(Map.of(
				"KADIKOY", new ServiceDistrict("KADIKOY", "Kadıköy", false, new BigDecimal("1.05"))));

		assertThat(SetupStatus.of(Optional.of(noneServed)).missing())
				.as("a row that exists but is switched off is not an area we work in (§4.5)")
				.containsExactly(MissingSetting.SERVICE_DISTRICTS);
		assertThat(SetupStatus.of(Optional.of(withDistricts(Map.of()))).missing())
				.as("and no row at all is the same answer")
				.containsExactly(MissingSetting.SERVICE_DISTRICTS);
	}

	@Test
	@DisplayName("a VAT rate of zero is nobody's rate, and each half is reported on its own")
	void eachVatRateIsItsOwnAnswer() {
		assertThat(SetupStatus.of(Optional.of(PriceBookFixture.seedWithVat("0.0000", "0.1000"))).missing())
				.containsExactly(MissingSetting.LABOUR_VAT_RATE);
		assertThat(SetupStatus.of(Optional.of(PriceBookFixture.seedWithVat("0.2000", "0.0000"))).missing())
				.as("§16 needs an accountant for two rates, not one — labour and material are taxed apart")
				.containsExactly(MissingSetting.MATERIAL_VAT_RATE);
	}

	@Test
	@DisplayName("a margin of zero prices the job at cost, which is not a price anybody chose")
	void marginOfZeroCountsAsMissing() {
		assertThat(SetupStatus.of(Optional.of(withMargin(BigDecimal.ZERO))).missing())
				.containsExactly(MissingSetting.MARGIN_RATIO);
	}

	private static PriceBook withDistricts(Map<String, ServiceDistrict> districts) {
		PriceBook seed = PriceBookFixture.seed();
		return rebuild(seed, seed.marginRatio(), districts);
	}

	private static PriceBook withMargin(BigDecimal margin) {
		PriceBook seed = PriceBookFixture.seed();
		return rebuild(seed, margin, seed.districts());
	}

	/** A record has no wither, and the two fields under test are eighteen components apart. */
	private static PriceBook rebuild(
			PriceBook seed, BigDecimal marginRatio, Map<String, ServiceDistrict> districts) {
		return new PriceBook(seed.versionCode(), seed.ceilingHeightM(), seed.grossToNetRatio(),
				seed.stage1OpeningRatio(), seed.doorOpeningM2(), seed.windowOpeningM2(), seed.crewSize(),
				seed.crewHoursPerDay(), seed.crewDayCost(), seed.dayRoundingTolerance(), marginRatio,
				seed.marginAlertThreshold(), seed.surveyAmountFactor(), seed.averageJobValue(),
				seed.labourVatRate(), seed.materialVatRate(), seed.baseBandRatio(), seed.items(),
				seed.modifiers(), seed.roomTypes(), districts);
	}
}
