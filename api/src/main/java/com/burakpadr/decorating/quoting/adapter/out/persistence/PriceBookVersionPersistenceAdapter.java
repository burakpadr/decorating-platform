package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.IncreaseTarget;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBookCoefficients;
import com.burakpadr.decorating.quoting.domain.model.PriceBookStructure;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import com.burakpadr.decorating.quoting.domain.model.ServiceDistrict;
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

	/**
	 * §5.11's two statements about labour, reconciled: an item's TL figure is its own minutes priced at
	 * the version's crew rate. Written once and used by every path that touches the column, because two
	 * copies of this expression is the defect ADR 0016 records in miniature. {@code i} is
	 * price_book_item, {@code b} is its price_book.
	 */
	private static final String DERIVED_LABOUR_COST =
			"round(i.labour_minutes * b.crew_day_cost / (b.crew_size * b.crew_hours_per_day * 60), 2)";

	@Override
	public void increaseItemCosts(UUID priceBookId, IncreaseTarget target, BigDecimal percent) {
		// One statement for all three targets: the half that is not being raised gets a factor of 1, and
		// round(x * 1, 2) is x. Branching the SQL would give the three targets three chances to diverge.
		BigDecimal factor = BigDecimal.ONE.add(percent.movePointLeft(2));
		BigDecimal labour = target.raisesLabour() ? factor : BigDecimal.ONE;
		BigDecimal material = target.raisesMaterial() ? factor : BigDecimal.ONE;

		// A labour rise is a rise in what the crew costs, not in fourteen unrelated figures. Raising
		// crew_day_cost and re-deriving is the same arithmetic — labour is linear in the crew rate — but
		// it leaves the version consistent, and it leaves a reader able to see WHY labour went up
		// (ADR 0016). round() rather than the column's own truncation: a rounding rule that differs from
		// the engine's HALF_UP by a cent per item is one nobody can reconcile against a quote.
		jdbc.update("UPDATE price_book SET crew_day_cost = round(crew_day_cost * ?, 2) WHERE id = ?",
				labour, priceBookId);
		// labour_cost is re-derived unconditionally, not only when labour was the target: the version is
		// already consistent, so for MATERIAL the expression returns what is there. One statement, no
		// branch, and the invariant holds by construction.
		jdbc.update("UPDATE price_book_item i SET labour_cost = " + DERIVED_LABOUR_COST + ", "
				+ "material_cost = round(i.material_cost * ?, 2) "
				+ "FROM price_book b WHERE b.id = i.price_book_id AND i.price_book_id = ?",
				material, priceBookId);
		// labour_minutes is untouched on purpose — see the port.
	}

	@Override
	public boolean isEditable(UUID id) {
		// One query, because the two halves of the answer have to be true at the same moment.
		Boolean editable = jdbc.queryForObject(
				"SELECT NOT b.active AND NOT EXISTS (SELECT 1 FROM quote q WHERE q.price_book_id = b.id) "
						+ "FROM price_book b WHERE b.id = ?",
				Boolean.class, id);
		return Boolean.TRUE.equals(editable);
	}

	@Override
	public PriceBookSummary createFromStructure(PriceBookStructure structure, String versionCode) {
		UUID id = Uuid7.generate();

		// Every money column written as an explicit zero. Left out, the schema would supply its own
		// defaults — a 30% margin and a 25,000 TL average job value — and a price that arrives with a
		// migration is what decision 0024 exists to prevent. Zero is how BOYA-69 reads "nobody entered
		// it", and activation refuses a version still at zero.
		jdbc.update("""
				INSERT INTO price_book (
				  id, version_code, active,
				  ceiling_height_m, gross_to_net_ratio, stage1_opening_ratio, door_opening_m2,
				  window_opening_m2, crew_size, crew_hours_per_day, day_rounding_tolerance,
				  survey_amount_factor, base_band_ratio,
				  crew_day_cost, margin_ratio, margin_alert_threshold, average_job_value,
				  labour_vat_rate, material_vat_rate)
				VALUES (?, ?, false, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0)
				""",
				id, versionCode, structure.ceilingHeightM(), structure.grossToNetRatio(),
				structure.stage1OpeningRatio(), structure.doorOpeningM2(), structure.windowOpeningM2(),
				structure.crewSize(), structure.crewHoursPerDay(), structure.dayRoundingTolerance(),
				structure.surveyAmountFactor(), structure.baseBandRatio());

		// labour_cost is zero because the crew rate is: the derivation holds at zero, so the version is
		// internally consistent from the first row and stays that way as the wizard fills it in.
		structure.items().values().forEach(item -> jdbc.update(
				"INSERT INTO price_book_item (id, price_book_id, code, unit, labour_cost, "
						+ "material_cost, labour_minutes) VALUES (?, ?, ?, ?, 0, 0, ?)",
				Uuid7.generate(), id, item.code().name(), item.unit().name(), item.labourMinutes()));

		structure.modifiers().values().forEach(modifier -> jdbc.update(
				"INSERT INTO price_modifier (id, price_book_id, code, factor, applies_to, scope_items) "
						+ "VALUES (?, ?, ?, ?, ?, ?::jsonb)",
				Uuid7.generate(), id, modifier.code().name(), modifier.factor(),
				modifier.target().name(),
				modifier.scopeItems().isEmpty() ? null : jsonArray(
						modifier.scopeItems().stream().map(Enum::name).toList())));

		structure.roomTypes().values().forEach(config -> jdbc.update(
				"INSERT INTO room_type_config (id, price_book_id, room_type, area_weight, "
						+ "perimeter_factor, paintable_ratio, required_photos) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)",
				Uuid7.generate(), id, config.roomType().name(), config.areaWeight(),
				config.perimeterFactor(), config.paintableRatio(),
				jsonArray(config.requiredPhotos().stream().map(Enum::name).toList())));

		// No districts. The installing business enters its own; ours are not their service area.
		return findById(id).orElseThrow();
	}

	@Override
	public void replaceDistricts(UUID priceBookId, List<ServiceDistrict> districts) {
		jdbc.update("DELETE FROM service_district WHERE price_book_id = ?", priceBookId);
		districts.forEach(district -> jdbc.update(
				"INSERT INTO service_district (id, price_book_id, district_code, display_name, active, "
						+ "district_factor) VALUES (?, ?, ?, ?, ?, ?)",
				Uuid7.generate(), priceBookId, district.districtCode(), district.displayName(),
				district.active(), district.districtFactor()));
	}

	/** A JSON array of quoted strings, for the two jsonb columns that hold code lists. */
	private static String jsonArray(List<String> values) {
		return values.stream()
				.map(value -> "\"" + value + "\"")
				.collect(java.util.stream.Collectors.joining(",", "[", "]"));
	}

	@Override
	public void updateCoefficients(UUID priceBookId, PriceBookCoefficients c) {
		jdbc.update("UPDATE price_book SET ceiling_height_m = ?, gross_to_net_ratio = ?, "
				+ "stage1_opening_ratio = ?, crew_size = ?, crew_hours_per_day = ?, crew_day_cost = ?, "
				+ "margin_ratio = ?, margin_alert_threshold = ?, labour_vat_rate = ?, material_vat_rate = ? "
				+ "WHERE id = ?",
				c.ceilingHeightM(), c.grossToNetRatio(), c.stage1OpeningRatio(), c.crewSize(),
				c.crewHoursPerDay(), c.crewDayCost(), c.marginRatio(), c.marginAlertThreshold(),
				c.labourVatRate(), c.materialVatRate(), priceBookId);
		// Second half of the same write, and the reason the two cannot be separated: crew size and crew
		// day cost are halves of one figure, and changing either without re-deriving leaves every item
		// stating a labour cost this version no longer implies (ADR 0016). The same expression the bulk
		// increase uses, so the two paths cannot round differently.
		jdbc.update("UPDATE price_book_item i SET labour_cost = " + DERIVED_LABOUR_COST + " "
				+ "FROM price_book b WHERE b.id = i.price_book_id AND i.price_book_id = ?", priceBookId);
	}

	@Override
	public void updateItem(UUID priceBookId, ItemCode code, BigDecimal materialCost,
			BigDecimal labourMinutes) {
		// labour_cost is not a parameter: it is what the new minutes cost at this version's crew rate
		// (ADR 0016). Accepting it would let the panel put back the disagreement V5 was written to
		// remove — and the row would look perfectly plausible while it did.
		jdbc.update("UPDATE price_book_item i SET material_cost = ?, labour_minutes = ?, "
				+ "labour_cost = round(? * b.crew_day_cost / (b.crew_size * b.crew_hours_per_day * 60), 2) "
				+ "FROM price_book b "
				+ "WHERE b.id = i.price_book_id AND i.price_book_id = ? AND i.code = ?",
				materialCost, labourMinutes, labourMinutes, priceBookId, code.name());
	}

	@Override
	public void activate(UUID id) {
		// Off before on: the partial unique index allows one active row, and Postgres checks it per
		// statement, so the reverse order fails even inside a transaction.
		jdbc.update("UPDATE price_book SET active = false WHERE active = true AND id <> ?", id);
		jdbc.update("UPDATE price_book SET active = true WHERE id = ?", id);
	}
}
