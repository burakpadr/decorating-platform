package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.CloseOutcome;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.QuoteStatus;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code quote_request} rows (§4.2, BOYA-25).
 *
 * <p>{@code JdbcTemplate} rather than an entity and a mapper, unlike the price book next door. The row
 * is written whole on every save because the merge already happened in the domain, so there is no
 * dirty-checking for an ORM to do and no lifecycle to manage — one {@code INSERT … ON CONFLICT DO
 * UPDATE} is the entire mapping, and it is readable as the thing it is.
 *
 * <p>Enum columns are {@code varchar} with a CHECK constraint (§4's convention), so the mapping is
 * {@code name()} out and {@code valueOf} back. An unknown value coming back is a migration this code
 * has not been taught about, and it fails rather than guessing.
 */
@Component
class QuoteRequestPersistenceAdapter implements QuoteRequestRepository {

	private final JdbcTemplate jdbc;

	QuoteRequestPersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(QuoteRequest request) {
		if (request.contactReason() != null) {
			// §3 gives AWAITING_CONTACT a reason and quote_request has no contact_reason column. Nothing
			// can reach that state yet (BOYA-57 sends the quote), and dropping the field silently is how a
			// call-back list ends up unable to say why anybody is on it.
			throw new IllegalStateException(
					"quote_request has no contact_reason column: add one before saving a request in "
							+ request.status());
		}
		StageOneAnswers answers = request.answers();
		jdbc.update("""
				INSERT INTO quote_request (
				  id, status, district_code, area_input, area_basis, layout, scope, furnishing,
				  door_count, door_colour_change, wall_condition, recapture_count, close_outcome)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET
				  status = excluded.status,
				  district_code = excluded.district_code,
				  area_input = excluded.area_input,
				  area_basis = excluded.area_basis,
				  layout = excluded.layout,
				  scope = excluded.scope,
				  furnishing = excluded.furnishing,
				  door_count = excluded.door_count,
				  door_colour_change = excluded.door_colour_change,
				  wall_condition = excluded.wall_condition,
				  recapture_count = excluded.recapture_count,
				  close_outcome = excluded.close_outcome,
				  -- BOYA-36 measures abandonment from this column, so a save that did not move it would
				  -- make an active form look abandoned.
				  updated_at = now()
				""",
				request.id(),
				request.status().name(),
				answers.districtCode(),
				answers.areaInput(),
				name(answers.areaBasis()),
				name(answers.layout()),
				name(answers.scope()),
				name(answers.furnishing()),
				answers.doorCount(),
				answers.doorColourChange(),
				name(answers.wallCondition()),
				request.recaptureCount(),
				name(request.closeOutcome()));
	}

	@Override
	public Optional<QuoteRequest> findById(UUID id) {
		return jdbc.query("""
				SELECT id, status, district_code, area_input, area_basis, layout, scope, furnishing,
				       door_count, door_colour_change, wall_condition, recapture_count, close_outcome
				FROM quote_request WHERE id = ?
				""", this::toDomain, id).stream().findFirst();
	}

	private QuoteRequest toDomain(ResultSet row, int rowNumber) throws SQLException {
		StageOneAnswers answers = new StageOneAnswers(
				row.getString("district_code"),
				row.getBigDecimal("area_input"),
				value(row.getString("area_basis"), AreaBasis::valueOf),
				value(row.getString("layout"), Layout::valueOf),
				value(row.getString("scope"), QuoteScope::valueOf),
				value(row.getString("furnishing"), Furnishing::valueOf),
				// getInt returns 0 for NULL, and zero doors is an answer somebody gave — so the column has
				// to be asked whether it was null before the value is believed.
				box(row, "door_count"),
				(Boolean) row.getObject("door_colour_change"),
				value(row.getString("wall_condition"), WallCondition::valueOf));

		return QuoteRequest.rehydrate(
				row.getObject("id", UUID.class),
				QuoteStatus.valueOf(row.getString("status")),
				row.getInt("recapture_count"),
				null,
				value(row.getString("close_outcome"), CloseOutcome::valueOf),
				answers);
	}

	private static Integer box(ResultSet row, String column) throws SQLException {
		int value = row.getInt(column);
		return row.wasNull() ? null : value;
	}

	private static <T extends Enum<T>> T value(String stored, java.util.function.Function<String, T> of) {
		return stored == null ? null : of.apply(stored);
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}
}
