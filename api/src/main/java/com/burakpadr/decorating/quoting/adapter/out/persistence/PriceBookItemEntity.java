package com.burakpadr.decorating.quoting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** The {@code price_book_item} row (§4.5). Costs, not sale prices; minutes are person-minutes. */
@Entity
@Table(name = "price_book_item")
class PriceBookItemEntity {

	@Id
	private UUID id;

	@Column(name = "price_book_id", nullable = false)
	private UUID priceBookId;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String unit;

	@Column(name = "labour_cost", nullable = false)
	private BigDecimal labourCost;

	@Column(name = "material_cost", nullable = false)
	private BigDecimal materialCost;

	@Column(name = "labour_minutes", nullable = false)
	private BigDecimal labourMinutes;

	protected PriceBookItemEntity() {}

	String getCode() {
		return code;
	}

	String getUnit() {
		return unit;
	}

	BigDecimal getLabourCost() {
		return labourCost;
	}

	BigDecimal getMaterialCost() {
		return materialCost;
	}

	BigDecimal getLabourMinutes() {
		return labourMinutes;
	}
}
