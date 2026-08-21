package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms.ConfirmedRoom;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.port.in.ConfirmRoomList;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import com.burakpadr.decorating.quoting.domain.port.out.PricedWithVersion;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import com.burakpadr.decorating.quoting.domain.service.RoomListDeriver;
import com.burakpadr.decorating.shared.Uuid7;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer accepts the list of areas (workflow §2.2, BOYA-37).
 *
 * <p>§2.2 explains why there is a screen here at all: "3+1" is four rooms to the person typing and
 * seven areas to the engine, so an expectation that is not set at the start becomes an abandoned
 * capture in the middle — "ortada bırakılan çekim, baştan söylenmiş uzun listeden kötüdür".
 */
@Service
class RoomListConfirmationService implements ConfirmRoomList {

	/** §2.4 budgets eight minutes for seven areas. Past this it is a typo or a hotel. */
	private static final int MOST_AREAS_ANYBODY_PHOTOGRAPHS = 20;

	private final QuoteRequestRepository requests;
	private final RoomRepository rooms;
	private final PriceBookRepository priceBooks;
	private final PricedWithVersion pricedWith;
	private final RoomListDeriver deriver = new RoomListDeriver();

	RoomListConfirmationService(QuoteRequestRepository requests, RoomRepository rooms,
			PriceBookRepository priceBooks, PricedWithVersion pricedWith) {
		this.requests = requests;
		this.rooms = rooms;
		this.priceBooks = priceBooks;
		this.pricedWith = pricedWith;
	}

	@Override
	@Transactional
	public ConfirmedRooms confirm(UUID quoteRequestId, List<RoomType> areas) {
		if (areas == null || areas.isEmpty()) {
			throw new IllegalArgumentException("there is nothing to photograph in an empty list");
		}
		if (areas.size() > MOST_AREAS_ANYBODY_PHOTOGRAPHS) {
			throw new IllegalArgumentException(
					"more areas than anybody photographs in an evening: " + areas.size());
		}

		QuoteRequest request = requests.findById(quoteRequestId)
				.orElseThrow(() -> new QuoteRequestNotFound(quoteRequestId.toString()));
		// Throws if the list has already been confirmed (§3 draws this arrow once), and it is also what
		// freezes the answers: the list was derived from them and the photographs are taken against it.
		QuoteRequest confirmed = request.confirmRoomList();

		ConfirmedRooms agreed = new ConfirmedRooms(
				deriver.label(areas, priceBook(quoteRequestId)).rooms().stream()
						.map(room -> new ConfirmedRoom(Uuid7.generate(), room.type(), room.label(),
								room.sortOrder(), room.requiredPhotos(), false))
						.toList());

		rooms.replaceAll(quoteRequestId, agreed);
		requests.save(confirmed);
		return agreed;
	}

	/**
	 * The version this request was priced with, falling back to the live one.
	 *
	 * <p>Not simply the active book. A zam between seeing the range and taking the photographs must not
	 * change what is asked for: the customer agreed to a list of frames, and §4.5's promise is that the
	 * version behind a figure stays the one that produced it. The fallback covers a request that has not
	 * been priced yet, which cannot reach this screen today but would otherwise fail obscurely.
	 */
	private PriceBook priceBook(UUID quoteRequestId) {
		return pricedWith.pricedWith(quoteRequestId)
				.flatMap(priceBooks::findById)
				.or(priceBooks::findActive)
				.orElseThrow(() -> new IllegalStateException(
						"no price book: the required frames per area belong to a version"));
	}
}
