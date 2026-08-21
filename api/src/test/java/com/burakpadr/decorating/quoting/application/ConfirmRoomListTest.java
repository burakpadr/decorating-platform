package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.QuoteStatus;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.ConfirmRoomList;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Confirming the room list (workflow §2.1–2.2, BOYA-37).
 *
 * <p>§2.2 says why this screen exists at all: "3+1" is four rooms to the customer and seven areas to
 * the engine, and an expectation that is not set at the start becomes an abandoned capture in the
 * middle — which is worse than a long list said honestly up front. So the list is derived, shown,
 * *changed by the customer*, and only then written.
 *
 * <p>The labels and the required photo roles are the server's, never the client's. A label is
 * customer-facing Turkish copy (§4.3's comment) and the roles decide what the analysis will be asked to
 * read; a client that could set either would be deciding what gets photographed.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConfirmRoomListTest {

	@Autowired
	private ConfirmRoomList rooms;

	@Autowired
	private EstimateStageOne estimates;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	/** A draft that has seen its range, which is where §2.2 picks up. */
	private UUID priced() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, WallCondition.MINOR, null));
		requests.save(draft);
		estimates.estimate(draft.id());
		return draft.id();
	}

	@Test
	@DisplayName("§2.1's seven areas for a 3+1, confirmed as they were derived")
	void confirmsTheDerivedList() {
		UUID id = priced();

		ConfirmedRooms confirmed = rooms.confirm(id, List.of(
				RoomType.LIVING_ROOM, RoomType.MASTER_BEDROOM, RoomType.BEDROOM, RoomType.BEDROOM,
				RoomType.KITCHEN, RoomType.BATHROOM, RoomType.HALLWAY));

		assertThat(confirmed.rooms()).extracting("label").containsExactly(
				"Salon", "Ebeveyn yatak odası", "Yatak odası 1", "Yatak odası 2",
				"Mutfak", "Banyo", "Koridor");
		assertThat(confirmed.photoCount())
				.as("§2.4's table: five for a living room and each bedroom, three for a kitchen and a "
						+ "hallway, two for a bathroom")
				.isEqualTo(28);
	}

	@Test
	@DisplayName("acceptance: the required frames come from room_type_config, not from the client")
	void readsRequiredPhotosFromThePriceBook() {
		UUID id = priced();

		ConfirmedRooms confirmed = rooms.confirm(id, List.of(RoomType.BATHROOM, RoomType.LIVING_ROOM));

		assertThat(confirmed.rooms().get(0).requiredPhotos())
				.as("§2.4: a bathroom is mostly tile and cupboard, so one general frame and the ceiling")
				.containsExactly(PhotoRole.WALL_1, PhotoRole.CEILING);
		assertThat(confirmed.rooms().get(1).requiredPhotos()).hasSize(5);
	}

	@Test
	@DisplayName("§2.2: the customer adds an area the layout never implied")
	void acceptsAnAddedArea() {
		UUID id = priced();

		ConfirmedRooms confirmed = rooms.confirm(id, List.of(
				RoomType.LIVING_ROOM, RoomType.BATHROOM, RoomType.BATHROOM, RoomType.STUDY,
				RoomType.BALCONY));

		assertThat(confirmed.rooms()).extracting("label").containsExactly(
				"Salon", "Banyo 1", "Banyo 2", "Çalışma odası", "Balkon");
		// A second bathroom is one of §2.2's ready-made buttons, and numbering only starts when a type
		// repeats — one bathroom is "Banyo", not "Banyo 1".
		assertThat(confirmed.photoCount()).isEqualTo(5 + 2 + 2 + 5 + 2);
	}

	@Test
	@DisplayName("§2.2: and removes one it does not want painted")
	void acceptsARemovedArea() {
		UUID id = priced();

		ConfirmedRooms confirmed = rooms.confirm(id, List.of(RoomType.LIVING_ROOM, RoomType.HALLWAY));

		assertThat(confirmed.rooms()).hasSize(2);
		assertThat(confirmed.photoCount()).isEqualTo(8);
	}

	@Test
	@DisplayName("confirming moves the request on, which is what makes the answers final")
	void movesTheRequestToPhotosPending() {
		UUID id = priced();

		rooms.confirm(id, List.of(RoomType.LIVING_ROOM));

		assertThat(requests.findById(id).orElseThrow().status())
				.as("§3's first arrow. From here the answers are fixed: the list was derived from them and "
						+ "the photographs will be taken against it")
				.isEqualTo(QuoteStatus.PHOTOS_PENDING);
	}

	@Test
	@DisplayName("the rows a reader would query: type, label, order, and nothing captured yet")
	void writesTheRooms() {
		UUID id = priced();

		rooms.confirm(id, List.of(RoomType.LIVING_ROOM, RoomType.BEDROOM, RoomType.BEDROOM));

		List<java.util.Map<String, Object>> stored = jdbc.queryForList(
				"SELECT room_type, label, sort_order, capture_complete FROM room "
						+ "WHERE quote_request_id = ? ORDER BY sort_order", id);
		assertThat(stored).hasSize(3);
		assertThat(stored.get(0)).containsEntry("room_type", "LIVING_ROOM")
				.containsEntry("label", "Salon").containsEntry("sort_order", 0)
				.containsEntry("capture_complete", false);
		assertThat(stored.get(2)).containsEntry("label", "Yatak odası 2");
	}

	@Test
	@DisplayName("confirming twice is refused rather than doubling the list")
	void refusesASecondConfirmation() {
		UUID id = priced();
		rooms.confirm(id, List.of(RoomType.LIVING_ROOM));

		assertThatThrownBy(() -> rooms.confirm(id, List.of(RoomType.KITCHEN)))
				.as("the second call would write a second set of rooms against the same photographs")
				.isInstanceOf(IllegalStateException.class);
		assertThat(jdbc.queryForObject(
						"SELECT count(*) FROM room WHERE quote_request_id = ?", Integer.class, id))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("an empty list is refused: there is nothing to photograph")
	void refusesAnEmptyList() {
		UUID id = priced();

		assertThatThrownBy(() -> rooms.confirm(id, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("a list nobody could photograph in an evening is refused too")
	void refusesAnAbsurdlyLongList() {
		UUID id = priced();
		List<RoomType> tooMany = java.util.Collections.nCopies(25, RoomType.BEDROOM);

		assertThatThrownBy(() -> rooms.confirm(id, tooMany))
				.as("§2.4 budgets eight minutes for seven areas; twenty-five is a typo or a hotel")
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("the required frames come from the version that priced it, not from whatever is live")
	void readsThePriceBookTheDraftWasPricedWith() {
		UUID id = priced();
		// A zam between seeing the range and taking the photographs must not change what is asked for:
		// the customer agreed to a list, and §4.5's promise is that the version behind a figure stays
		// the one that produced it.
		UUID priced = jdbc.queryForObject(
				"SELECT price_book_id FROM quote_request WHERE id = ?", UUID.class, id);
		jdbc.update("UPDATE price_book SET active = false WHERE id = ?", priced);
		try {
			assertThat(rooms.confirm(id, List.of(RoomType.BATHROOM)).rooms().get(0).requiredPhotos())
					.hasSize(2);
		}
		finally {
			jdbc.update("UPDATE price_book SET active = true WHERE id = ?", priced);
		}
	}
}
