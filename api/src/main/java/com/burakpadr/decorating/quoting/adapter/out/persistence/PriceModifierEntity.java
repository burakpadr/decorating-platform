package com.burakpadr.decorating.quoting.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The {@code price_modifier} row (§4.5).
 *
 * <p>{@code scopeItems} is the raw jsonb text. It is parsed in the mapper rather than mapped into a
 * collection, so the JSON shape stays one small, testable step away from the domain — and a scope
 * naming an item code this version of the software does not know cannot stop the book from loading.
 */
@Entity
@Table(name = "price_modifier")
class PriceModifierEntity {

	@Id
	private UUID id;

	@Column(name = "price_book_id", nullable = false)
	private UUID priceBookId;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private BigDecimal factor;

	@Column(name = "applies_to", nullable = false)
	private String appliesTo;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "scope_items")
	private String scopeItems;

	protected PriceModifierEntity() {}

	String getCode() {
		return code;
	}

	BigDecimal getFactor() {
		return factor;
	}

	String getAppliesTo() {
		return appliesTo;
	}

	String getScopeItems() {
		return scopeItems;
	}
}
