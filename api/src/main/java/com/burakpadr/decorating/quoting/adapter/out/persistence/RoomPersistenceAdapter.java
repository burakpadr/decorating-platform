package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms.ConfirmedRoom;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code room} rows (§4.3).
 *
 * <p>The required frames are not stored. They belong to the price book version
 * ({@code room_type_config.required_photos}) and the request records which version priced it, so
 * writing them here would be a second copy free to disagree with the first — and the one that decides
 * what the analysis reads is the price book. What is stored is the agreement: which areas, in what
 * order, called what.
 */
@Component
class RoomPersistenceAdapter implements RoomRepository {

	private final JdbcTemplate jdbc;

	RoomPersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void replaceAll(UUID quoteRequestId, ConfirmedRooms rooms) {
		// Cleared first: UNIQUE (quote_request_id, sort_order) makes a partial rewrite collide, and a
		// half-replaced list is a capture screen showing rooms from two different agreements.
		jdbc.update("DELETE FROM room WHERE quote_request_id = ?", quoteRequestId);

		List<ConfirmedRoom> ordered = rooms.rooms();
		jdbc.batchUpdate("""
				INSERT INTO room (id, quote_request_id, room_type, label, sort_order, capture_complete)
				VALUES (?, ?, ?, ?, ?, ?)
				""", new BatchPreparedStatementSetter() {

			@Override
			public void setValues(PreparedStatement statement, int index) throws SQLException {
				ConfirmedRoom room = ordered.get(index);
				statement.setObject(1, room.id());
				statement.setObject(2, quoteRequestId);
				statement.setString(3, room.type().name());
				statement.setString(4, room.label());
				statement.setInt(5, room.sortOrder());
				statement.setBoolean(6, room.captureComplete());
			}

			@Override
			public int getBatchSize() {
				return ordered.size();
			}
		});
	}

	@Override
	public ConfirmedRooms findByQuoteRequest(UUID quoteRequestId) {
		List<ConfirmedRoom> rooms = jdbc.query("""
				SELECT r.id, r.room_type, r.label, r.sort_order, r.capture_complete, c.required_photos
				FROM room r
				JOIN quote_request q ON q.id = r.quote_request_id
				JOIN room_type_config c
				  ON c.room_type = r.room_type
				 AND c.price_book_id = COALESCE(
				       q.price_book_id, (SELECT id FROM price_book WHERE active = true))
				WHERE r.quote_request_id = ?
				ORDER BY r.sort_order
				""", (row, index) -> new ConfirmedRoom(
						row.getObject("id", UUID.class),
						RoomType.valueOf(row.getString("room_type")),
						row.getString("label"),
						row.getInt("sort_order"),
						photoRoles(row.getString("required_photos")),
						row.getBoolean("capture_complete")),
				quoteRequestId);
		return new ConfirmedRooms(rooms);
	}

	@Override
	public Optional<UUID> quoteRequestOf(UUID roomId) {
		return jdbc.query("SELECT quote_request_id FROM room WHERE id = ?",
				(row, index) -> row.getObject(1, UUID.class), roomId).stream().findFirst();
	}

	/**
	 * The jsonb array of role names. Parsed by hand for the same reason
	 * {@code QuoteRequestPersistenceAdapter} writes one: the values are enum names, so the only
	 * characters that can appear are {@code [A-Z_0-9]} and there is nothing to escape.
	 */
	private static List<PhotoRole> photoRoles(String json) {
		List<PhotoRole> roles = new ArrayList<>();
		for (String name : json.replaceAll("[\\[\\]\"\\s]", "").split(",")) {
			if (!name.isEmpty()) {
				roles.add(PhotoRole.valueOf(name));
			}
		}
		return roles;
	}
}
