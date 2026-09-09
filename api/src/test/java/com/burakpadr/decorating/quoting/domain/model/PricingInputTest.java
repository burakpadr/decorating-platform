package com.burakpadr.decorating.quoting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §5.1's claim, made checkable: stage 1 and stage 2 build the same object.
 *
 * <p>The engine is allowed to branch on {@code source} in exactly two places §5.5 and §5.9 name — the
 * opening deduction and the band. Everything else it reads has to arrive the same way from both
 * stages, or the range a customer was shown and the quote they are sent were computed from different
 * declarations, and nothing about either figure would say so.
 *
 * <p>So the factory takes the answers and both stages call it. What is asserted here is that the only
 * things a stage chooses for itself are the rooms and the source.
 */
class PricingInputTest {

	private static final StageOneAnswers ANSWERS = new StageOneAnswers(
			"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
			QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, WallCondition.MINOR, null);

	@Test
	@DisplayName("the two stages differ in their rooms and their source, and in nothing else")
	void differsOnlyInRoomsAndSource() {
		PricingInput declared = PricingInput.declaredBy(ANSWERS, new BigDecimal("92.00"), false,
				List.of(RoomInput.declared(RoomType.BEDROOM, WallCondition.MINOR)),
				PricingSource.STAGE_1);

		PricingInput analysed = PricingInput.declaredBy(ANSWERS, new BigDecimal("92.00"), false,
				List.of(RoomInput.analysed(RoomType.BEDROOM,
						List.of(new SurfaceInput("WALL_1", Coating.PAINTED, Tone.LIGHT, FillerBand.LOW,
								false, Moisture.NONE, false, new BigDecimal("0.900"))),
						1, 1, 1, 0, false, CeilingFinding.none())),
				PricingSource.STAGE_2);

		assertThat(analysed).usingRecursiveComparison()
				.ignoringFields("rooms", "source")
				.isEqualTo(declared);
		assertThat(analysed.source()).isEqualTo(PricingSource.STAGE_2);
	}

	@Test
	@DisplayName("the door total stays the customer's, even when the analysis counted its own")
	void keepsTheDeclaredDoorTotal() {
		// §6: detection flags a disagreement for the operator, it does not override a declaration
		// (BOYA-52). The counted doors are per room and do a different job — deducting openings from the
		// wall area (§5.5) — so the two numbers never meet.
		PricingInput input = PricingInput.declaredBy(ANSWERS, new BigDecimal("92.00"), false,
				List.of(RoomInput.analysed(RoomType.BEDROOM, List.of(), 3, 2, 1, 0, false,
						CeilingFinding.none())),
				PricingSource.STAGE_2);

		assertThat(input.doorCount()).isEqualTo(8);
		assertThat(input.rooms().getFirst().doorCount()).isEqualTo(3);
	}

	@Test
	@DisplayName("what §2.1 never asked is assumed the same way for both stages")
	void assumesTheUnaskedQuestionsIdentically() {
		// A lift assumed present, because §5.6 charges for the absence of one: the direction to be wrong
		// in when nobody has been asked.
		PricingInput input = PricingInput.declaredBy(ANSWERS, new BigDecimal("92.00"), false,
				List.of(RoomInput.declared(RoomType.BEDROOM, WallCondition.GOOD)),
				PricingSource.STAGE_2);

		assertThat(input.hasElevator()).isTrue();
		assertThat(input.rush()).isFalse();
		assertThat(input.doorCountEstimated()).isFalse();
	}
}
