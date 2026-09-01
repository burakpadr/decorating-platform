package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.CeilingFinding;
import com.burakpadr.decorating.quoting.domain.model.Coating;
import com.burakpadr.decorating.quoting.domain.model.CrackLevel;
import com.burakpadr.decorating.quoting.domain.model.FillerBand;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.SurfaceFinding;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import com.burakpadr.decorating.quoting.domain.port.out.RoomAnalysisRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code room_analysis} and its {@code surface_finding} rows (§4.4, BOYA-49).
 *
 * <p>Two tables and one write. The findings are read back from the columns and never from
 * {@code raw_response}, which is §4.4's whole reason for keeping both: the engine must not parse JSON
 * and a calibration report must be a plain join. The JSON is carried along as the audit trail and as
 * the only place the values with no column of their own survive.
 *
 * <p>Saving replaces. {@code room_id} is UNIQUE, so a re-analysis after a recapture is the analysis —
 * and the delete has to take the old surfaces with it, or a two-wall reading lands on top of a
 * four-wall one and the engine prices six. The cascade on {@code surface_finding} does that; doing it
 * in one transaction is what stops a room existing for a moment with no surfaces at all.
 */
@Component
class RoomAnalysisPersistenceAdapter implements RoomAnalysisRepository {

	private final JdbcTemplate jdbc;

	RoomAnalysisPersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional
	public void save(RoomAnalysis analysis) {
		jdbc.update("DELETE FROM room_analysis WHERE room_id = ?", analysis.roomId());

		UUID id = Uuid7.generate();
		jdbc.update("""
				INSERT INTO room_analysis (
				  id, room_id, raw_response, model_version, prompt_version, confidence,
				  reported_confidence, furnishing, door_count, window_count, radiator_count,
				  cornice, downlight_count, ceiling_staining, ceiling_filler, ceiling_confidence,
				  unusable_photos, notes)
				VALUES (?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", (PreparedStatement statement) -> {
			statement.setObject(1, id);
			statement.setObject(2, analysis.roomId());
			statement.setString(3, analysis.rawResponse());
			statement.setString(4, analysis.modelVersion());
			statement.setString(5, analysis.promptVersion());
			// §6's average over the planes — already at the column's scale, because the evaluator and
			// the row have to be reading the same number.
			statement.setBigDecimal(6, analysis.roomConfidence());
			statement.setBigDecimal(7, analysis.reportedConfidence());
			statement.setString(8, analysis.furnishing().name());
			statement.setInt(9, analysis.doorCount());
			statement.setInt(10, analysis.windowCount());
			statement.setInt(11, analysis.radiatorCount());
			statement.setBoolean(12, analysis.cornice());
			statement.setInt(13, analysis.downlightCount());
			statement.setString(14, analysis.ceiling().staining().name());
			statement.setString(15, analysis.ceiling().filler().name());
			statement.setBigDecimal(16, analysis.ceilingConfidence());
			statement.setArray(17, textArray(statement, analysis.unusablePhotos()));
			statement.setArray(18, textArray(statement, analysis.notes()));
		});

		List<SurfaceFinding> surfaces = analysis.surfaces();
		jdbc.batchUpdate("""
				INSERT INTO surface_finding (
				  id, room_analysis_id, surface_id, coating, current_tone, filler_ratio,
				  skim_coat_required, crack_level, moisture, wallpaper, confidence)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement statement, int index) throws SQLException {
				SurfaceFinding surface = surfaces.get(index);
				statement.setObject(1, Uuid7.generate());
				statement.setObject(2, id);
				statement.setString(3, surface.surfaceId());
				statement.setString(4, surface.coating().name());
				statement.setString(5, surface.tone().name());
				statement.setString(6, surface.fillerBand().name());
				statement.setBoolean(7, surface.skimCoatRequired());
				statement.setString(8, surface.crackLevel().name());
				statement.setString(9, surface.moisture().name());
				statement.setBoolean(10, surface.wallpaper());
				statement.setBigDecimal(11, surface.confidence());
			}

			@Override
			public int getBatchSize() {
				return surfaces.size();
			}
		});
	}

	@Override
	public Optional<RoomAnalysis> findByRoom(UUID roomId) {
		return read("WHERE ra.room_id = ?", roomId).stream().findFirst();
	}

	@Override
	public List<RoomAnalysis> findByQuoteRequest(UUID quoteRequestId) {
		return read("JOIN room r ON r.id = ra.room_id "
				+ "WHERE r.quote_request_id = ? ORDER BY r.sort_order", quoteRequestId);
	}

	/**
	 * Both reads in one query each. The surfaces come back joined rather than as a second round trip
	 * per room: a 3+1 home is seven analyses, and seven extra queries to answer one question is how a
	 * screen that lists a queue starts costing what a screen that opens one costs.
	 */
	private List<RoomAnalysis> read(String where, UUID argument) {
		Map<UUID, Row> byAnalysis = new LinkedHashMap<>();
		jdbc.query("""
				SELECT ra.id, ra.room_id, ra.raw_response, ra.model_version, ra.prompt_version,
				       ra.reported_confidence, ra.furnishing, ra.door_count, ra.window_count,
				       ra.radiator_count, ra.cornice, ra.downlight_count, ra.ceiling_staining,
				       ra.ceiling_filler, ra.ceiling_confidence, ra.unusable_photos, ra.notes,
				       sf.surface_id, sf.coating, sf.current_tone, sf.filler_ratio,
				       sf.skim_coat_required, sf.crack_level, sf.moisture, sf.wallpaper,
				       sf.confidence AS surface_confidence
				FROM room_analysis ra
				LEFT JOIN surface_finding sf ON sf.room_analysis_id = ra.id
				%s
				""".formatted(where), row -> {
			Row analysis = byAnalysis.computeIfAbsent(row.getObject("id", UUID.class), key -> row(row));
			if (row.getString("surface_id") != null) {
				analysis.surfaces().add(surface(row));
			}
		}, argument);

		return byAnalysis.values().stream().map(Row::toDomain).toList();
	}

	private Row row(ResultSet row) {
		try {
			return new Row(
					row.getObject("room_id", UUID.class),
					row.getString("prompt_version"),
					row.getString("model_version"),
					row.getString("raw_response"),
					new CeilingFinding(
							Moisture.valueOf(row.getString("ceiling_staining")),
							FillerBand.valueOf(row.getString("ceiling_filler"))),
					row.getBigDecimal("ceiling_confidence"),
					row.getBoolean("cornice"),
					row.getInt("downlight_count"),
					Furnishing.valueOf(row.getString("furnishing")),
					row.getInt("door_count"),
					row.getInt("window_count"),
					row.getInt("radiator_count"),
					row.getBigDecimal("reported_confidence"),
					strings(row.getArray("unusable_photos")),
					strings(row.getArray("notes")),
					new ArrayList<>());
		}
		catch (SQLException unreadable) {
			throw new IllegalStateException("room_analysis row could not be read", unreadable);
		}
	}

	private static SurfaceFinding surface(ResultSet row) {
		try {
			return new SurfaceFinding(
					row.getString("surface_id"),
					Coating.valueOf(row.getString("coating")),
					Tone.valueOf(row.getString("current_tone")),
					FillerBand.valueOf(row.getString("filler_ratio")),
					row.getBoolean("skim_coat_required"),
					CrackLevel.valueOf(row.getString("crack_level")),
					Moisture.valueOf(row.getString("moisture")),
					row.getBoolean("wallpaper"),
					row.getBigDecimal("surface_confidence"));
		}
		catch (SQLException unreadable) {
			throw new IllegalStateException("surface_finding row could not be read", unreadable);
		}
	}

	private static java.sql.Array textArray(PreparedStatement statement, List<String> values)
			throws SQLException {
		return statement.getConnection().createArrayOf("text", values.toArray(String[]::new));
	}

	private static List<String> strings(java.sql.Array array) throws SQLException {
		return List.of((String[]) array.getArray());
	}

	/** One analysis while its surfaces are still arriving from the join. */
	private record Row(
			UUID roomId, String promptVersion, String modelVersion, String rawResponse,
			CeilingFinding ceiling, BigDecimal ceilingConfidence, boolean cornice, int downlightCount,
			Furnishing furnishing, int doorCount, int windowCount, int radiatorCount,
			BigDecimal reportedConfidence, List<String> unusablePhotos, List<String> notes,
			List<SurfaceFinding> surfaces) {

		RoomAnalysis toDomain() {
			return new RoomAnalysis(roomId, promptVersion, modelVersion, rawResponse,
					surfaces, ceiling, ceilingConfidence, cornice, downlightCount,
					furnishing, doorCount, windowCount, radiatorCount, reportedConfidence,
					unusablePhotos, notes);
		}
	}
}
