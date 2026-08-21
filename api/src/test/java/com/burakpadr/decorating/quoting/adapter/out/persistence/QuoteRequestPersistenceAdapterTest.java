package com.burakpadr.decorating.quoting.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.CloseOutcome;
import com.burakpadr.decorating.quoting.domain.model.ContactReason;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.QuoteStatus;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Storing a draft (§4.2, BOYA-25).
 *
 * <p>The ticket's acceptance is that every step reaches the server, because people abandon a form on
 * one device and continue on another. So what is asserted here is the round trip: an answer written on
 * one call is still there on the next read, and the row a reader would query holds what the domain
 * thinks it holds.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class QuoteRequestPersistenceAdapterTest {

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE district_code = 'TEST-DISTRICT' OR district_code IS NULL");
	}

	private static final StageOneAnswers FULL = new StageOneAnswers(
			"TEST-DISTRICT", new BigDecimal("92.00"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
			QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, WallCondition.MINOR, null);

	@Test
	@DisplayName("a draft survives the round trip with every answer it was given")
	void savesAndReadsBackEveryAnswer() {
		UUID id = Uuid7.generate();

		requests.save(QuoteRequest.draft(id).answer(FULL));

		QuoteRequest read = requests.findById(id).orElseThrow();
		assertThat(read.id()).isEqualTo(id);
		assertThat(read.status()).isEqualTo(QuoteStatus.DRAFT);
		assertThat(read.answers()).isEqualTo(FULL);
	}

	@Test
	@DisplayName("saving twice updates the row rather than failing on its key")
	void savingTwiceUpdates() {
		UUID id = Uuid7.generate();
		requests.save(QuoteRequest.draft(id).answer(
				new StageOneAnswers("TEST-DISTRICT", null, null, null, null, null, null, null, null, null)));

		requests.save(requests.findById(id).orElseThrow().answer(
				new StageOneAnswers(null, null, null, Layout.STUDIO, null, null, null, null, null, null)));

		QuoteRequest read = requests.findById(id).orElseThrow();
		assertThat(read.answers().districtCode())
				.as("the second call answered the layout and must not have un-answered the district")
				.isEqualTo("TEST-DISTRICT");
		assertThat(read.answers().layout()).isEqualTo(Layout.STUDIO);
		assertThat(jdbc.queryForObject(
						"SELECT count(*) FROM quote_request WHERE id = ?", Integer.class, id))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("the row holds what a reader would query, column by column")
	void writesTheColumnsAReaderReads() {
		UUID id = Uuid7.generate();
		requests.save(QuoteRequest.draft(id).answer(FULL));

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM quote_request WHERE id = ?", id);

		assertThat(row.get("status")).isEqualTo("DRAFT");
		assertThat(row.get("district_code")).isEqualTo("TEST-DISTRICT");
		assertThat((BigDecimal) row.get("area_input")).isEqualByComparingTo("92.00");
		assertThat(row.get("area_basis")).isEqualTo("NET");
		assertThat(row.get("layout")).isEqualTo("THREE_PLUS_ONE");
		assertThat(row.get("scope")).isEqualTo("WHOLE_HOME");
		assertThat(row.get("furnishing")).isEqualTo("FURNISHED");
		assertThat(row.get("door_count")).isEqualTo(8);
		assertThat(row.get("door_colour_change")).isEqualTo(true);
		assertThat(row.get("wall_condition")).isEqualTo("MINOR");
		assertThat(row.get("recapture_count")).isEqualTo(0);
		assertThat(row.get("customer_id"))
				.as("§4.1: stage 1 is anonymous and must not produce a customer")
				.isNull();
		assertThat(row.get("net_area"))
				.as("derived when the estimate is computed (BOYA-29), not while the form is being filled")
				.isNull();
	}

	@Test
	@DisplayName("an unanswered question is null in the row, not a zero or an empty string")
	void leavesUnansweredQuestionsNull() {
		UUID id = Uuid7.generate();
		requests.save(QuoteRequest.draft(id));

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM quote_request WHERE id = ?", id);

		assertThat(row.get("district_code")).isNull();
		assertThat(row.get("door_count"))
				.as("zero doors is an answer; this request has not reached that screen")
				.isNull();
		assertThat(row.get("door_colour_change")).isNull();
	}

	@Test
	@DisplayName("updated_at moves when the draft is written again")
	void touchesUpdatedAt() throws InterruptedException {
		UUID id = Uuid7.generate();
		requests.save(QuoteRequest.draft(id).answer(FULL));
		Instant first = read(id);

		Thread.sleep(10);
		requests.save(requests.findById(id).orElseThrow().answer(
				new StageOneAnswers(null, null, null, Layout.STUDIO, null, null, null, null, null, null)));

		assertThat(read(id))
				.as("BOYA-36 measures abandonment from this column; a draft that never looks touched "
						+ "looks abandoned the moment it is created")
				.isAfter(first);
	}

	@Test
	@DisplayName("a request nobody created is not found, rather than an empty one being invented")
	void unknownIdIsEmpty() {
		assertThat(requests.findById(Uuid7.generate())).isEmpty();
	}

	@Test
	@DisplayName("a state the schema cannot store is refused loudly")
	void refusesWhatTheSchemaCannotHold() {
		// quote_request has close_outcome and no contact_reason column, and §3 gives AWAITING_CONTACT a
		// reason. Nothing can reach that state yet (BOYA-57 sends the quote), so rather than dropping the
		// field on the floor the adapter says the column is missing.
		QuoteRequest awaiting = QuoteRequest.rehydrate(Uuid7.generate(), QuoteStatus.AWAITING_CONTACT,
				0, ContactReason.ACCEPTED, null, StageOneAnswers.empty());

		assertThatThrownBy(() -> requests.save(awaiting))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("contact_reason");
	}

	@Test
	@DisplayName("a closed request keeps its outcome")
	void storesTheCloseOutcome() {
		UUID id = Uuid7.generate();

		requests.save(QuoteRequest.rehydrate(
				id, QuoteStatus.CLOSED, 1, null, CloseOutcome.EXPIRED, FULL));

		QuoteRequest read = requests.findById(id).orElseThrow();
		assertThat(read.closeOutcome()).isEqualTo(CloseOutcome.EXPIRED);
		assertThat(read.recaptureCount()).isEqualTo(1);
	}

	private Instant read(UUID id) {
		return jdbc.queryForObject(
				"SELECT updated_at FROM quote_request WHERE id = ?", Instant.class, id);
	}
}
