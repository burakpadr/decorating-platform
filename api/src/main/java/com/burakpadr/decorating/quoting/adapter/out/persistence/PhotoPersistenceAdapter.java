package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * {@code photo} rows (§4.3).
 *
 * <p>Upsert rather than insert-or-update by hand: a row is written twice by design — once when the key
 * is reserved and again when the object arrives — and the second write must not depend on the first
 * still being where the service left it.
 *
 * <p>{@code delete_after} is not set here. §12 counts retention from the day the request closes, not
 * from the day the photograph was taken, so the column is filled by the job that closes it — writing a
 * date now would delete photographs while the customer is still deciding.
 */
@Component
class PhotoPersistenceAdapter implements PhotoRepository {

	private static final RowMapper<Photo> AS_PHOTO = (row, index) -> new Photo(
			row.getObject("id", UUID.class),
			row.getObject("room_id", UUID.class),
			PhotoRole.valueOf(row.getString("role")),
			row.getString("storage_key"),
			instant(row.getTimestamp("captured_at")),
			instant(row.getTimestamp("uploaded_at")),
			(Integer) row.getObject("width"),
			(Integer) row.getObject("height"),
			(Integer) row.getObject("byte_size"),
			row.getBigDecimal("quality_score"),
			row.getBoolean("low_quality_flag"));

	private final JdbcTemplate jdbc;

	PhotoPersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(Photo photo) {
		jdbc.update("""
				INSERT INTO photo (id, room_id, role, storage_key, captured_at, uploaded_at,
				                   width, height, byte_size, quality_score, low_quality_flag)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (id) DO UPDATE SET
				  captured_at = EXCLUDED.captured_at,
				  uploaded_at = EXCLUDED.uploaded_at,
				  width = EXCLUDED.width,
				  height = EXCLUDED.height,
				  byte_size = EXCLUDED.byte_size,
				  quality_score = EXCLUDED.quality_score,
				  low_quality_flag = EXCLUDED.low_quality_flag
				""",
				photo.id(), photo.roomId(), photo.role().name(), photo.storageKey(),
				timestamp(photo.capturedAt()), timestamp(photo.uploadedAt()),
				photo.width(), photo.height(), photo.byteSize(),
				photo.qualityScore(), photo.lowQualityFlag());
	}

	@Override
	public Optional<Photo> findById(UUID photoId) {
		return first(jdbc.query("SELECT * FROM photo WHERE id = ?", AS_PHOTO, photoId));
	}

	@Override
	public Optional<Photo> findByRoomAndRole(UUID roomId, PhotoRole role) {
		// Newest first: only DETAIL can hold more than one, and the callers that ask this question are
		// the ones §4.3 allows a single row for.
		return first(jdbc.query(
				"SELECT * FROM photo WHERE room_id = ? AND role = ? ORDER BY id DESC LIMIT 1",
				AS_PHOTO, roomId, role.name()));
	}

	@Override
	public void delete(UUID photoId) {
		jdbc.update("DELETE FROM photo WHERE id = ?", photoId);
	}

	@Override
	public Optional<UUID> quoteRequestOf(UUID photoId) {
		return first(jdbc.query("""
				SELECT r.quote_request_id FROM photo p JOIN room r ON r.id = p.room_id WHERE p.id = ?
				""", (row, index) -> row.getObject(1, UUID.class), photoId));
	}

	private static <T> Optional<T> first(List<T> rows) {
		return rows.stream().findFirst();
	}

	private static Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	private static Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}
}
