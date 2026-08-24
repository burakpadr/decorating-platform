package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code room} rows for a request (§4.3).
 *
 * <p>Written once, as a set: the list is agreed in one act (workflow §2.2), and
 * {@code UNIQUE (quote_request_id, sort_order)} says the same thing in the schema — rows added one at a
 * time from two places would collide on the order they are captured in.
 */
public interface RoomRepository {

	void replaceAll(UUID quoteRequestId, ConfirmedRooms rooms);

	ConfirmedRooms findByQuoteRequest(UUID quoteRequestId);

	/**
	 * Which request this room belongs to, or empty if there is no such room.
	 *
	 * <p>Asked before a photograph is reserved against it (§9). The room id arrives from the client and
	 * the session names a different id, so this is the join that decides whether the two are the same
	 * customer — and answering the same way for "no such room" as for "not yours" is what stops the
	 * question from being a way to map other people's rooms.
	 */
	Optional<UUID> quoteRequestOf(UUID roomId);
}
