package com.burakpadr.decorating.quoting.domain.service;

import com.burakpadr.decorating.quoting.domain.model.DerivedRoom;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomList;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns "3+1, the whole home" into the list of areas to photograph (§2.1).
 *
 * <p>The gap this closes is the one §2.2 exists to manage: "3+1" is four rooms to the customer and
 * seven areas to us, because painting a whole home includes the kitchen, the bathroom and the hallway.
 * Deriving the longer list and showing it up front is deliberate — a capture abandoned in the middle
 * is worse than a long list stated honestly at the start.
 *
 * <p>Pure, like the engine. A room list is a function of the layout, the scope and the price book's
 * room types; the customer then adds and removes areas at §2.2, which is a different operation and
 * not this one's business.
 */
public final class RoomListDeriver {

	/**
	 * The areas a layout implies, in capture order: the living room first, because it is the largest and
	 * sets the customer's expectation of how long a room takes, and the wet rooms and hallway last,
	 * because they need the fewest frames and are the easiest to finish on.
	 *
	 * <p>A studio has no hallway and no separate bedroom — its one room is the living space. Every other
	 * layout has exactly one master bedroom, which is why §5.3 gives it its own weight: the largest
	 * bedroom is not the same size as the smallest.
	 */
	private static final Map<Layout, List<RoomType>> AREAS = areas();

	private static Map<Layout, List<RoomType>> areas() {
		Map<Layout, List<RoomType>> areas = new EnumMap<>(Layout.class);
		areas.put(Layout.STUDIO, List.of(
				RoomType.LIVING_ROOM, RoomType.KITCHEN, RoomType.BATHROOM));
		areas.put(Layout.ONE_PLUS_ONE, List.of(
				RoomType.LIVING_ROOM, RoomType.MASTER_BEDROOM,
				RoomType.KITCHEN, RoomType.BATHROOM, RoomType.HALLWAY));
		areas.put(Layout.TWO_PLUS_ONE, List.of(
				RoomType.LIVING_ROOM, RoomType.MASTER_BEDROOM, RoomType.BEDROOM,
				RoomType.KITCHEN, RoomType.BATHROOM, RoomType.HALLWAY));
		areas.put(Layout.THREE_PLUS_ONE, List.of(
				RoomType.LIVING_ROOM, RoomType.MASTER_BEDROOM, RoomType.BEDROOM, RoomType.BEDROOM,
				RoomType.KITCHEN, RoomType.BATHROOM, RoomType.HALLWAY));
		areas.put(Layout.FOUR_PLUS_ONE, List.of(
				RoomType.LIVING_ROOM, RoomType.MASTER_BEDROOM, RoomType.BEDROOM, RoomType.BEDROOM,
				RoomType.BEDROOM, RoomType.KITCHEN, RoomType.BATHROOM, RoomType.HALLWAY));
		areas.put(Layout.FIVE_PLUS_ONE, List.of(
				RoomType.LIVING_ROOM, RoomType.MASTER_BEDROOM, RoomType.BEDROOM, RoomType.BEDROOM,
				RoomType.BEDROOM, RoomType.BEDROOM, RoomType.KITCHEN, RoomType.BATHROOM,
				RoomType.HALLWAY));
		return areas;
	}

	/**
	 * @param selected which types are being painted; ignored for {@link QuoteScope#WHOLE_HOME}, because
	 *     the whole home is the whole home and a stale selection must not quietly shrink it
	 */
	public RoomList derive(Layout layout, QuoteScope scope, Set<RoomType> selected, PriceBook book) {
		List<RoomType> areas = AREAS.get(layout);
		if (areas == null) {
			throw new IllegalArgumentException("no area list for layout " + layout);
		}
		if (scope == QuoteScope.SELECTED_ROOMS) {
			// Counts still come from the layout: the customer answered which kinds of room are being
			// painted, not how many of each the flat has.
			areas = areas.stream().filter(selected::contains).toList();
			if (areas.isEmpty()) {
				throw new IllegalArgumentException(
						"nothing selected to paint: " + layout + " has none of " + selected);
			}
		}

		return label(areas, book);
	}

	/**
	 * Labels and orders a list of areas that is already decided.
	 *
	 * <p>Public because §2.2 lets the customer change the list — add a second bathroom, drop the balcony
	 * — and the result still has to be labelled and numbered by the same rule. A second copy of that rule
	 * would be two answers to "what is this room called", and the label is what the customer reads on the
	 * capture screen and the operator reads in the quote.
	 */
	public RoomList label(List<RoomType> areas, PriceBook book) {
		Map<RoomType, Integer> total = new EnumMap<>(RoomType.class);
		areas.forEach(type -> total.merge(type, 1, Integer::sum));

		Map<RoomType, Integer> seen = new EnumMap<>(RoomType.class);
		List<DerivedRoom> rooms = new ArrayList<>();
		for (RoomType type : areas) {
			int ordinal = seen.merge(type, 1, Integer::sum);
			// Numbered only where there is something to tell apart: "Yatak odası 1" with no second one
			// reads like part of the list went missing.
			String label = total.get(type) > 1
					? RoomLabels.of(type) + " " + ordinal
					: RoomLabels.of(type);
			rooms.add(new DerivedRoom(type, label, rooms.size(), book.roomType(type).requiredPhotos()));
		}
		return new RoomList(rooms);
	}
}
