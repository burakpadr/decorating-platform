package com.burakpadr.decorating.quoting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** The {@code service_district} row (§4.5). {@code displayName} is customer-facing Turkish. */
@Entity
@Table(name = "service_district")
class ServiceDistrictEntity {

	@Id
	private UUID id;

	@Column(name = "price_book_id", nullable = false)
	private UUID priceBookId;

	@Column(name = "district_code", nullable = false)
	private String districtCode;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "district_factor", nullable = false)
	private BigDecimal districtFactor;

	protected ServiceDistrictEntity() {}

	String getDistrictCode() {
		return districtCode;
	}

	String getDisplayName() {
		return displayName;
	}

	boolean isActive() {
		return active;
	}

	BigDecimal getDistrictFactor() {
		return districtFactor;
	}
}
