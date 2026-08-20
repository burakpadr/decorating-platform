package com.burakpadr.decorating.quoting.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.quoting.domain.PriceBookFixture;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomList;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Turning "3+1, the whole home" into the list of areas to photograph (§2.1, §17).
 *
 * <p>The list is shown to the customer before they start shooting (§2.2), and the number on it is a
 * promise: "3+1" is four rooms to them and seven areas to us, so a list that under-counts produces a
 * capture abandoned halfway — which is worse than a long list stated honestly at the start.
 *
 * <p>Pure, like the engine: a room list is a function of the layout, the scope and the price book's
 * room types, and nothing else.
 */
class RoomListDeriverTest {

	private final RoomListDeriver deriver = new RoomListDeriver();

	@ParameterizedTest(name = "{0}, whole home → {1} areas, {2} frames")
	@CsvSource({
		"STUDIO,3,10",
		"ONE_PLUS_ONE,5,18",
		"TWO_PLUS_ONE,6,23",
		"THREE_PLUS_ONE,7,28",
		"FOUR_PLUS_ONE,8,33",
		"FIVE_PLUS_ONE,9,38"})
	@DisplayName("every layout painted whole produces its own list, and its own frame count")
	void everyLayoutPaintedWhole(String layout, int areas, int frames) {
		RoomList list = deriver.derive(
				Layout.valueOf(layout), QuoteScope.WHOLE_HOME, Set.of(), PriceBookFixture.seed());

		assertThat(list.size()).isEqualTo(areas);
		assertThat(list.photoCount()).isEqualTo(frames);
	}

	@Test
	@DisplayName("§2.1's example: 3+1 whole home is salon, three bedrooms, kitchen, bathroom, hallway")
	void reproducesTheExampleInTheWorkflowDocument() {
		RoomList list = deriver.derive(
				Layout.THREE_PLUS_ONE, QuoteScope.WHOLE_HOME, Set.of(), PriceBookFixture.seed());

		assertThat(list.rooms()).extracting("type").containsExactly(
				RoomType.LIVING_ROOM, RoomType.MASTER_BEDROOM, RoomType.BEDROOM, RoomType.BEDROOM,
				RoomType.KITCHEN, RoomType.BATHROOM, RoomType.HALLWAY);
		assertThat(list.rooms()).extracting("label").containsExactly(
				"Salon", "Ebeveyn yatak odası", "Yatak odası 1", "Yatak odası 2",
				"Mutfak", "Banyo", "Koridor");
		assertThat(list.photoCount())
				.as("the 28 frames §2.4 quotes for a 3+1, and the number the customer is shown up front")
				.isEqualTo(28);
	}

	@Test
	@DisplayName("a room type appearing once is not numbered")
	void doesNotNumberASingleRoom() {
		RoomList list = deriver.derive(
				Layout.TWO_PLUS_ONE, QuoteScope.WHOLE_HOME, Set.of(), PriceBookFixture.seed());

		assertThat(list.rooms()).extracting("label")
				.as("'Yatak odası 1' with no second one reads like something is missing")
				.contains("Yatak odası")
				.doesNotContain("Yatak odası 1");
	}

	@Test
	@DisplayName("capture order runs from the living room to the wet rooms, without ties")
	void ordersTheAreasForCapture() {
		RoomList list = deriver.derive(
				Layout.FOUR_PLUS_ONE, QuoteScope.WHOLE_HOME, Set.of(), PriceBookFixture.seed());

		assertThat(list.rooms()).extracting("sortOrder").containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
		assertThat(list.rooms().getFirst().type()).isEqualTo(RoomType.LIVING_ROOM);
		assertThat(list.rooms().getLast().type()).isEqualTo(RoomType.HALLWAY);
	}

	@Test
	@DisplayName("each area asks for the frames its room type asks for (§2.4)")
	void takesTheFramesFromThePriceBook() {
		RoomList list = deriver.derive(
				Layout.THREE_PLUS_ONE, QuoteScope.WHOLE_HOME, Set.of(), PriceBookFixture.seed());

		assertThat(list.rooms().getFirst().requiredPhotos())
				.as("four walls and the ceiling, in shooting order")
				.containsExactly(PhotoRole.WALL_1, PhotoRole.WALL_2, PhotoRole.WALL_3, PhotoRole.WALL_4,
						PhotoRole.CEILING);
		assertThat(list.rooms().stream()
						.filter(room -> room.type() == RoomType.BATHROOM).findFirst().orElseThrow()
						.requiredPhotos())
				.as("a bathroom is mostly tile, so one general shot and the ceiling")
				.containsExactly(PhotoRole.WALL_1, PhotoRole.CEILING);
		assertThat(list.rooms()).allSatisfy(room ->
				assertThat(room.requiredPhotos())
						.as("%s: the close-up of a crack is offered after the required frames, never required",
								room.label())
						.doesNotContain(PhotoRole.DETAIL));
	}

	@Test
	@DisplayName("painting only some rooms derives only those, with the layout's own counts")
	void derivesOnlyTheSelectedRooms() {
		RoomList list = deriver.derive(Layout.THREE_PLUS_ONE, QuoteScope.SELECTED_ROOMS,
				Set.of(RoomType.LIVING_ROOM, RoomType.BEDROOM), PriceBookFixture.seed());

		assertThat(list.rooms()).extracting("label")
				.as("two bedrooms because that is what a 3+1 has, not because anyone typed 2")
				.containsExactly("Salon", "Yatak odası 1", "Yatak odası 2");
		assertThat(list.photoCount()).isEqualTo(15);
	}

	@Test
	@DisplayName("a selected type the layout does not have simply is not there")
	void ignoresASelectionTheLayoutCannotHold() {
		RoomList list = deriver.derive(Layout.STUDIO, QuoteScope.SELECTED_ROOMS,
				Set.of(RoomType.LIVING_ROOM, RoomType.BEDROOM), PriceBookFixture.seed());

		assertThat(list.rooms()).extracting("type").containsExactly(RoomType.LIVING_ROOM);
	}

	@Test
	@DisplayName("a selection that leaves nothing to paint is refused, not quoted")
	void refusesAnEmptySelection() {
		assertThatThrownBy(() -> deriver.derive(Layout.STUDIO, QuoteScope.SELECTED_ROOMS,
						Set.of(RoomType.STUDY), PriceBookFixture.seed()))
				.as("there is no quote to make for no rooms, and the customer must be sent back a step")
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("painting the whole home ignores any selection that came with it")
	void ignoresTheSelectionWhenPaintingEverything() {
		RoomList whole = deriver.derive(Layout.TWO_PLUS_ONE, QuoteScope.WHOLE_HOME,
				Set.of(RoomType.BATHROOM), PriceBookFixture.seed());

		assertThat(whole.size()).isEqualTo(6);
	}
}
