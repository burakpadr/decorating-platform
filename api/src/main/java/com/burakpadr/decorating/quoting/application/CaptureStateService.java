package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.CaptureState;
import com.burakpadr.decorating.quoting.domain.model.CaptureState.CaptureArea;
import com.burakpadr.decorating.quoting.domain.model.CaptureState.CaptureFrame;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms.ConfirmedRoom;
import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.port.in.ReadCaptureState;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoRepository;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The join of what was agreed against what has arrived (workflow §2.4, BOYA-42).
 *
 * <p>Two reads and no writes. The required frames come from the room list, which took them from the
 * price book version that priced the request; the photographs come from the rows the browser completed.
 * Neither side is authoritative about the other, which is the point — the screen has to show a customer
 * the difference.
 *
 * <p>Only uploaded photographs are matched. A reservation the browser never PUT to is a row with no
 * object behind it, and treating it as a photograph would make the capture look finished to everyone
 * except §3's submit guard.
 */
@Service
class CaptureStateService implements ReadCaptureState {

	private final QuoteRequestRepository requests;
	private final RoomRepository rooms;
	private final PhotoRepository photos;

	CaptureStateService(QuoteRequestRepository requests, RoomRepository rooms, PhotoRepository photos) {
		this.requests = requests;
		this.rooms = rooms;
		this.photos = photos;
	}

	@Override
	@Transactional(readOnly = true)
	public CaptureState of(UUID quoteRequestId) {
		if (requests.findById(quoteRequestId).isEmpty()) {
			throw new QuoteRequestNotFound(String.valueOf(quoteRequestId));
		}

		Map<UUID, Map<PhotoRole, Photo>> arrived = photos.findByQuoteRequest(quoteRequestId).stream()
				.filter(Photo::isUploaded)
				.collect(Collectors.groupingBy(Photo::roomId,
						// A room holds one frame per role, except DETAIL — which is not a required frame and
						// so never asked for here. Merging on collision keeps the newest rather than throwing.
						Collectors.toMap(Photo::role, photo -> photo, (older, newer) -> newer)));

		return new CaptureState(rooms.findByQuoteRequest(quoteRequestId).rooms().stream()
				.map(room -> new CaptureArea(room.id(), room.type(), room.label(), room.sortOrder(),
						framesOf(room, arrived.getOrDefault(room.id(), Map.of()))))
				.toList());
	}

	private static List<CaptureFrame> framesOf(ConfirmedRoom room, Map<PhotoRole, Photo> arrived) {
		return room.requiredPhotos().stream()
				.map(role -> {
					Photo photo = arrived.get(role);
					return photo == null
							? CaptureFrame.outstanding(role)
							: new CaptureFrame(role, photo.id(), true, photo.lowQualityFlag());
				})
				.toList();
	}
}
