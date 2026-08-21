package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.StageOneEstimate;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Stage 1's instant range (§7, §5.6, BOYA-29).
 *
 * <p>The ticket's two acceptance criteria are the first two tests: the same answers always produce the
 * same range, and "emin değilim" widens it visibly. Both are about trust rather than arithmetic — a
 * range that moves between two identical submissions is a range nobody can quote back to us, and a
 * band that does not widen when the customer says they do not know is a promise the survey cannot keep.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EstimateStageOneTest {

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

	private static StageOneAnswers answers(WallCondition walls) {
		return new StageOneAnswers("KADIKOY", new BigDecimal("92"), AreaBasis.NET,
				Layout.THREE_PLUS_ONE, QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, walls, null);
	}

	private UUID draftWith(StageOneAnswers answers) {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(answers);
		requests.save(draft);
		return draft.id();
	}

	@Test
	@DisplayName("acceptance: the same answers always produce the same range")
	void isDeterministic() {
		UUID first = draftWith(answers(WallCondition.MINOR));
		UUID second = draftWith(answers(WallCondition.MINOR));

		StageOneEstimate one = estimates.estimate(first);
		StageOneEstimate two = estimates.estimate(second);

		assertThat(one.low()).isEqualByComparingTo(two.low());
		assertThat(one.high()).isEqualByComparingTo(two.high());
		assertThat(estimates.estimate(first).low())
				.as("and asking twice for the same request answers the same, so a reload is not a new price")
				.isEqualByComparingTo(one.low());
	}

	@Test
	@DisplayName("acceptance: 'emin değilim' widens the band visibly, without moving its midpoint")
	void unsureWidensTheBand() {
		StageOneEstimate known = estimates.estimate(draftWith(answers(WallCondition.MINOR)));
		StageOneEstimate unsure = estimates.estimate(draftWith(answers(WallCondition.UNSURE)));

		assertThat(unsure.bandRatio())
				.as("§5.9: 12%% base plus 15 points for not knowing")
				.isEqualByComparingTo("0.27");
		assertThat(width(unsure)).isGreaterThan(width(known));
		// §5.9's other half: the midpoint is where the arithmetic puts it. UNSURE prices near MINOR, so a
		// customer who does not know is not quoted a higher price — they are quoted a wider one.
		assertThat(midpoint(unsure))
				.isGreaterThan(midpoint(known));       // 0.20 filler against 0.15, and no more
		assertThat(midpoint(unsure).divide(midpoint(known), 4, java.math.RoundingMode.HALF_UP))
				.isLessThan(new BigDecimal("1.05"));
	}

	@Test
	@DisplayName("the range is written to the draft, with the version and the net area behind it")
	void storesWhatItAnswered() {
		UUID id = draftWith(answers(WallCondition.MINOR));

		StageOneEstimate estimate = estimates.estimate(id);

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM quote_request WHERE id = ?", id);
		assertThat((BigDecimal) row.get("estimate_low")).isEqualByComparingTo(estimate.low());
		assertThat((BigDecimal) row.get("estimate_high")).isEqualByComparingTo(estimate.high());
		assertThat((BigDecimal) row.get("net_area")).isEqualByComparingTo("92.00");
		assertThat(row.get("price_book_id"))
				.as("§4.5: a figure the customer was shown must stay explainable after the next zam")
				.isNotNull();
	}

	@Test
	@DisplayName("a gross area is converted with the version's own ratio, and the band pays for it")
	void convertsAGrossArea() {
		UUID id = draftWith(new StageOneAnswers("KADIKOY", new BigDecimal("112"), AreaBasis.GROSS,
				Layout.THREE_PLUS_ONE, QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true,
				WallCondition.MINOR, null));

		StageOneEstimate estimate = estimates.estimate(id);

		assertThat(estimate.netArea()).isEqualByComparingTo("91.84");
		assertThat(estimate.bandRatio())
				.as("§5.9 charges 5 points of band for a converted area, because it is an assumption")
				.isEqualByComparingTo("0.17");
		assertThat(jdbc.queryForObject(
						"SELECT net_area FROM quote_request WHERE id = ?", BigDecimal.class, id))
				.isEqualByComparingTo("91.84");
	}

	@Test
	@DisplayName("the areas the estimate assumed come back with it, so the customer can be told")
	void answersWithTheDerivedAreas() {
		StageOneEstimate estimate = estimates.estimate(draftWith(answers(WallCondition.GOOD)));

		assertThat(estimate.rooms().rooms()).extracting("label")
				.contains("Salon", "Ebeveyn yatak odası", "Mutfak", "Banyo", "Koridor");
		assertThat(estimate.rooms().rooms()).hasSize(7);
	}

	@Test
	@DisplayName("§2.2: every kind of area comes back with the frames it needs")
	void answersWithTheFramesEachKindOfAreaNeeds() {
		StageOneEstimate estimate = estimates.estimate(draftWith(answers(WallCondition.GOOD)));

		// Workflow §2.2 lets the customer add a second bathroom, a study or a balcony, and the screen has
		// to keep telling the truth about how many photographs that comes to. The frames belong to the
		// price book version (§5.3), so they are answered here rather than copied into the client, where a
		// second copy would be free to drift from the version that priced the range.
		assertThat(estimate.requiredPhotosByType())
				.containsEntry(RoomType.LIVING_ROOM, 5)
				.containsEntry(RoomType.KITCHEN, 3)
				.containsEntry(RoomType.BATHROOM, 2)
				.containsEntry(RoomType.STUDY, 5)
				.containsEntry(RoomType.BALCONY, 2)
				.as("every kind, not only the ones this layout derived: the addable ones are exactly the "
						+ "ones the list does not already have")
				.hasSize(RoomType.values().length);
	}

	@Test
	@DisplayName("a selection of areas prices only those areas")
	void pricesOnlyTheSelectedAreas() {
		UUID whole = draftWith(answers(WallCondition.GOOD));
		UUID some = draftWith(new StageOneAnswers("KADIKOY", new BigDecimal("92"), AreaBasis.NET,
				Layout.THREE_PLUS_ONE, QuoteScope.SELECTED_ROOMS, Furnishing.FURNISHED, 8, true,
				WallCondition.GOOD, Set.of(RoomType.LIVING_ROOM, RoomType.KITCHEN)));

		StageOneEstimate everything = estimates.estimate(whole);
		StageOneEstimate selected = estimates.estimate(some);

		assertThat(selected.rooms().rooms()).hasSize(2);
		assertThat(selected.high())
				.as("two areas out of seven cannot cost what all seven do")
				.isLessThan(everything.high());
	}

	@Test
	@DisplayName("a draft missing an answer is refused rather than priced on a guess")
	void refusesAnIncompleteDraft() {
		UUID id = draftWith(new StageOneAnswers("KADIKOY", new BigDecimal("92"), AreaBasis.NET,
				Layout.THREE_PLUS_ONE, QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, null, null));

		assertThatThrownBy(() -> estimates.estimate(id))
				.as("a default wall condition would be the engine answering a question nobody asked, and "
						+ "the customer would be shown a number for a job they did not describe")
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("estimating does not move the request out of DRAFT")
	void leavesTheRequestInDraft() {
		UUID id = draftWith(answers(WallCondition.MINOR));

		estimates.estimate(id);

		assertThat(requests.findById(id).orElseThrow().status())
				.as("§3: the range is shown and the customer decides — confirming the room list is the "
						+ "next transition, and it is theirs to make")
				.isEqualTo(com.burakpadr.decorating.quoting.domain.model.QuoteStatus.DRAFT);
	}

	@Test
	@DisplayName("an estimate can be asked for again after an answer changes, and it moves")
	void reflectsAChangedAnswer() {
		UUID id = draftWith(answers(WallCondition.GOOD));
		StageOneEstimate before = estimates.estimate(id);

		requests.save(requests.findById(id).orElseThrow().answer(new StageOneAnswers(
				null, null, null, null, null, null, null, null, WallCondition.MAJOR, null)));
		StageOneEstimate after = estimates.estimate(id);

		assertThat(after.low())
				.as("MAJOR puts a skim coat on a quarter of the walls (§5.6); a range that did not move "
						+ "would mean the answer was read once and cached")
				.isGreaterThan(before.low());
	}

	private static BigDecimal width(StageOneEstimate estimate) {
		return estimate.high().subtract(estimate.low());
	}

	private static BigDecimal midpoint(StageOneEstimate estimate) {
		return estimate.high().add(estimate.low()).divide(new BigDecimal("2"));
	}
}
