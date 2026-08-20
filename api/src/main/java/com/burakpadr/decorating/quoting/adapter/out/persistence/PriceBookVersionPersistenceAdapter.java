package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.IncreaseTarget;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookVersionRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Creating, listing and activating price book versions (§4.5, §7).
 *
 * <p>SQL rather than JPA, deliberately. Copying a version is four set-based inserts over 65 rows; the
 * same thing through an entity manager is 65 objects loaded, detached, re-identified and flushed, for
 * no gain and one more way to end up with half a version. A copy that lands partly is worse than one
 * that fails.
 *
 * <p>The parent row gets a {@link Uuid7} because it is the id quotes point at for years. The child
 * rows keep {@code gen_random_uuid()}: nothing outside the table references them, and generating 65
 * ids in the application would turn one statement into a round trip per row.
 */
@Repository
class PriceBookVersionPersistenceAdapter implements PriceBookVersionRepository {

	private static final RowMapper<PriceBookSummary> SUMMARY = (rs, row) -> new PriceBookSummary(
			rs.getObject("id", UUID.class),
			rs.getString("version_code"),
			rs.getBoolean("active"),
			rs.getTimestamp("created_at").toInstant());

	private final JdbcTemplate jdbc;

	PriceBookVersionPersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<PriceBookSummary> findAll() {
		return jdbc.query(
				"SELECT id, version_code, active, created_at FROM price_book ORDER BY created_at DESC",
				SUMMARY);
	}

	@Override
	public Optional<PriceBookSummary> findById(UUID id) {
		return jdbc.query("SELECT id, version_code, active, created_at FROM price_book WHERE id = ?",
				SUMMARY, id).stream().findFirst();
	}

	@Override
	public boolean existsByVersionCode(String versionCode) {
		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM price_book WHERE version_code = ?", Integer.class, versionCode);
		return count != null && count > 0;
	}

	@Override
	public PriceBookSummary copy(UUID sourceId, String versionCode) {
		UUID id = Uuid7.generate();

		// Inactive by construction: the column is written as false rather than copied, so a copy of the
		// active version cannot arrive active and trip the single-active index on its way in.
		jdbc.update("""
				INSERT INTO price_book (
				  id, version_code, active,
				  ceiling_height_m, gross_to_net_ratio, stage1_opening_ratio, door_opening_m2,
				  window_opening_m2, crew_size, crew_hours_per_day, crew_day_cost,
				  day_rounding_tolerance, margin_ratio, margin_alert_threshold, survey_amount_factor,
				  labour_vat_rate, material_vat_rate, base_band_ratio)
				SELECT
				  ?, ?, false,
				  ceiling_height_m, gross_to_net_ratio, stage1_opening_ratio, door_opening_m2,
				  window_opening_m2, crew_size, crew_hours_per_day, crew_day_cost,
				  day_rounding_tolerance, margin_ratio, margin_alert_threshold, survey_amount_factor,
				  labour_vat_rate, material_vat_rate, base_band_ratio
				FROM price_book WHERE id = ?
				""", id, versionCode, sourceId);

		// All four children, or the version prices nothing at all (§4.5, docs/decisions/0010).
		jdbc.update("""
				INSERT INTO price_book_item (id, price_book_id, code, unit, labour_cost, material_cost,
				  labour_minutes)
				SELECT gen_random_uuid(), ?, code, unit, labour_cost, material_cost, labour_minutes
				FROM price_book_item WHERE price_book_id = ?
				""", id, sourceId);
		jdbc.update("""
				INSERT INTO price_modifier (id, price_book_id, code, factor, applies_to, scope_items)
				SELECT gen_random_uuid(), ?, code, factor, applies_to, scope_items
				FROM price_modifier WHERE price_book_id = ?
				""", id, sourceId);
		jdbc.update("""
				INSERT INTO room_type_config (id, price_book_id, room_type, area_weight, perimeter_factor,
				  paintable_ratio, required_photos)
				SELECT gen_random_uuid(), ?, room_type, area_weight, perimeter_factor, paintable_ratio,
				  required_photos
				FROM room_type_config WHERE price_book_id = ?
				""", id, sourceId);
		jdbc.update("""
				INSERT INTO service_district (id, price_book_id, district_code, display_name, active,
				  district_factor)
				SELECT gen_random_uuid(), ?, district_code, display_name, active, district_factor
				FROM service_district WHERE price_book_id = ?
				""", id, sourceId);

		return findById(id).orElseThrow();
	}

	@Override
	public void increaseItemCosts(UUID priceBookId, IncreaseTarget target, BigDecimal percent) {
		// One statement for all three targets: the half that is not being raised gets a factor of 1, and
		// round(x * 1, 2) is x. Branching the SQL would give the three targets three chances to diverge.
		BigDecimal factor = BigDecimal.ONE.add(percent.movePointLeft(2));
		BigDecimal labour = target.raisesLabour() ? factor : BigDecimal.ONE;
		BigDecimal material = target.raisesMaterial() ? factor : BigDecimal.ONE;

		// round() rather than the column's own truncation: a rounding rule that differs from the
		// engine's HALF_UP by a cent per item is one nobody can reconcile against a quote.
		jdbc.update("UPDATE price_book_item SET labour_cost = round(labour_cost * ?, 2), "
				+ "material_cost = round(material_cost * ?, 2) WHERE price_book_id = ?",
				labour, material, priceBookId);
		// labour_minutes is untouched on purpose — see the port.
	}

	@Override
	public void activate(UUID id) {
		// Off before on: the partial unique index allows one active row, and Postgres checks it per
		// statement, so the reverse order fails even inside a transaction.
		jdbc.update("UPDATE price_book SET active = false WHERE active = true AND id <> ?", id);
		jdbc.update("UPDATE price_book SET active = true WHERE id = ?", id);
	}
}
