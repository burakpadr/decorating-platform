package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import java.util.List;
import java.util.UUID;

/**
 * The customer accepts the list of areas to photograph (§7's {@code /rooms/confirm}, workflow §2.2).
 *
 * <p>The caller sends types, in the order they will be captured, with repeats for repeated areas — two
 * bathrooms are {@code BATHROOM} twice. Labels, ordering and the required frames per area are derived
 * here; §2.2's ready-made buttons ("ikinci banyo", "çalışma odası", "balkon") are just types the client
 * appends.
 *
 * @throws com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound if there is no such request
 * @throws IllegalStateException if the list has already been confirmed — §3 allows this once
 * @throws IllegalArgumentException if the list is empty or longer than anybody could photograph
 */
public interface ConfirmRoomList {

	ConfirmedRooms confirm(UUID quoteRequestId, List<RoomType> areas);
}
