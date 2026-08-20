package com.burakpadr.decorating.quoting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The {@code price_book} row (§4.5).
 *
 * <p>A separate class from {@code PriceBook} on purpose: the domain model may not carry a framework
 * annotation, and {@code ArchitectureRulesTest} fails the build if it does. The cost is one explicit
 * mapper; what it buys is a domain that compiles without Hibernate on the classpath.
 *
 * <p>Read-only in this slice. The columns the engine does not use — {@code survey_amount_factor},
 * {@code created_at} — are deliberately absent: Hibernate's {@code validate} checks that what is
 * declared exists, not that everything that exists is declared, so an entity can be exactly as wide
 * as its purpose. The version-cloning use case (BOYA-18) will widen it.
 */
@Entity
@Table(name = "price_book")
class PriceBookEntity {

	@Id
	private UUID id;

	@Column(name = "version_code", nullable = false)
	private String versionCode;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "ceiling_height_m", nullable = false)
	private BigDecimal ceilingHeightM;

	@Column(name = "gross_to_net_ratio", nullable = false)
	private BigDecimal grossToNetRatio;

	@Column(name = "stage1_opening_ratio", nullable = false)
	private BigDecimal stage1OpeningRatio;

	@Column(name = "door_opening_m2", nullable = false)
	private BigDecimal doorOpeningM2;

	@Column(name = "window_opening_m2", nullable = false)
	private BigDecimal windowOpeningM2;

	@Column(name = "crew_size", nullable = false)
	private int crewSize;

	@Column(name = "crew_hours_per_day", nullable = false)
	private BigDecimal crewHoursPerDay;

	@Column(name = "crew_day_cost", nullable = false)
	private BigDecimal crewDayCost;

	@Column(name = "day_rounding_tolerance", nullable = false)
	private BigDecimal dayRoundingTolerance;

	@Column(name = "margin_ratio", nullable = false)
	private BigDecimal marginRatio;

	@Column(name = "margin_alert_threshold", nullable = false)
	private BigDecimal marginAlertThreshold;

	@Column(name = "labour_vat_rate", nullable = false)
	private BigDecimal labourVatRate;

	@Column(name = "material_vat_rate", nullable = false)
	private BigDecimal materialVatRate;

	@Column(name = "base_band_ratio", nullable = false)
	private BigDecimal baseBandRatio;

	protected PriceBookEntity() {}

	UUID getId() {
		return id;
	}

	String getVersionCode() {
		return versionCode;
	}

	boolean isActive() {
		return active;
	}

	BigDecimal getCeilingHeightM() {
		return ceilingHeightM;
	}

	BigDecimal getGrossToNetRatio() {
		return grossToNetRatio;
	}

	BigDecimal getStage1OpeningRatio() {
		return stage1OpeningRatio;
	}

	BigDecimal getDoorOpeningM2() {
		return doorOpeningM2;
	}

	BigDecimal getWindowOpeningM2() {
		return windowOpeningM2;
	}

	int getCrewSize() {
		return crewSize;
	}

	BigDecimal getCrewHoursPerDay() {
		return crewHoursPerDay;
	}

	BigDecimal getCrewDayCost() {
		return crewDayCost;
	}

	BigDecimal getDayRoundingTolerance() {
		return dayRoundingTolerance;
	}

	BigDecimal getMarginRatio() {
		return marginRatio;
	}

	BigDecimal getMarginAlertThreshold() {
		return marginAlertThreshold;
	}

	BigDecimal getLabourVatRate() {
		return labourVatRate;
	}

	BigDecimal getMaterialVatRate() {
		return materialVatRate;
	}

	BigDecimal getBaseBandRatio() {
		return baseBandRatio;
	}
}
