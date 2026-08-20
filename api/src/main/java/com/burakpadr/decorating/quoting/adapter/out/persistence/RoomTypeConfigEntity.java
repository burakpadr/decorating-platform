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
 * The {@code room_type_config} row (§4.5, §5.3).
 *
 * <p>{@code requiredPhotos} is the raw jsonb array of {@code photo.role} values, parsed in the mapper.
 * It rides with the pricing coefficients because it answers the same question: how much of this kind of
 * room is paintable, and therefore how much of it has to be seen (§2.4).
 */
@Entity
@Table(name = "room_type_config")
class RoomTypeConfigEntity {

	@Id
	private UUID id;

	@Column(name = "price_book_id", nullable = false)
	private UUID priceBookId;

	@Column(name = "room_type", nullable = false)
	private String roomType;

	@Column(name = "area_weight", nullable = false)
	private BigDecimal areaWeight;

	@Column(name = "perimeter_factor", nullable = false)
	private BigDecimal perimeterFactor;

	@Column(name = "paintable_ratio", nullable = false)
	private BigDecimal paintableRatio;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "required_photos", nullable = false)
	private String requiredPhotos;

	protected RoomTypeConfigEntity() {}

	String getRoomType() {
		return roomType;
	}

	BigDecimal getAreaWeight() {
		return areaWeight;
	}

	BigDecimal getPerimeterFactor() {
		return perimeterFactor;
	}

	BigDecimal getPaintableRatio() {
		return paintableRatio;
	}

	String getRequiredPhotos() {
		return requiredPhotos;
	}
}
