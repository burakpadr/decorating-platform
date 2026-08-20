package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PricedQuote;
import com.burakpadr.decorating.quoting.domain.model.PricingInput;
import com.burakpadr.decorating.quoting.domain.model.PricingSource;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculation;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculationCommand;
import com.burakpadr.decorating.quoting.domain.model.RoomInput;
import com.burakpadr.decorating.quoting.domain.model.RoomList;
import com.burakpadr.decorating.quoting.domain.port.in.CalculateQuote;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import com.burakpadr.decorating.quoting.domain.service.PricingEngine;
import com.burakpadr.decorating.quoting.domain.service.RoomListDeriver;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The internal tool's one use case: turn the form into a price (workflow §12, increment 1).
 *
 * <p>Three steps, and the order is the interesting part. The area is converted to net first, because
 * everything downstream divides by it; the room list is derived from the layout, because the engine
 * takes rooms and not "3+1"; and only then is the engine asked, with the flag that says the area was an
 * assumption so §5.9 can widen the band for it.
 *
 * <p>The conversion uses the active version's own ratio rather than a constant. A gross-to-net figure
 * that lived in code would be a coefficient nobody could calibrate, and this is the one it would be
 * worst to hide — every square metre in the quote comes through it.
 */
@Service
@Transactional(readOnly = true)
class QuoteCalculationService implements CalculateQuote {

	private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

	private final PriceBookRepository priceBooks;
	private final RoomListDeriver rooms = new RoomListDeriver();
	private final PricingEngine engine = new PricingEngine();

	QuoteCalculationService(PriceBookRepository priceBooks) {
		this.priceBooks = priceBooks;
	}

	@Override
	public QuoteCalculation calculate(QuoteCalculationCommand command) {
		PriceBook book = priceBooks.findActive().orElseThrow(() -> new IllegalStateException(
				"no active price book: nothing can be priced until one version is active"));

		boolean areaWasGross = command.areaBasis() == AreaBasis.GROSS;
		BigDecimal netArea = areaWasGross
				? command.area().multiply(book.grossToNetRatio()).setScale(2, RoundingMode.HALF_UP)
				: command.area().setScale(2, RoundingMode.HALF_UP);
		if (netArea.signum() <= 0) {
			throw new IllegalArgumentException("an area has to be above zero");
		}

		RoomList roomList = rooms.derive(command.layout(), command.scope(), command.selectedRooms(), book);

		PricingInput input = new PricingInput(
				command.districtCode(),
				netArea,
				areaWasGross,
				roomList.rooms().stream()
						.map(room -> RoomInput.declared(room.type(), command.wallCondition()))
						.toList(),
				command.furnishing(),
				command.doorCount(),
				command.doorColourChange(),
				command.doorCountEstimated(),
				command.hasElevator(),
				command.rush(),
				// A hand-entered job has no photographs, so it is priced exactly as stage 1 prices one:
				// the flat opening ratio and the declared wall condition, band and all.
				PricingSource.STAGE_1);

		PricedQuote quote = engine.price(input, book);
		return new QuoteCalculation(quote, roomList, netArea, areaWasGross, book.versionCode());
	}
}
