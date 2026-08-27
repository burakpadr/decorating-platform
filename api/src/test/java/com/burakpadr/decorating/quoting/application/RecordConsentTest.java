package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.Consent;
import com.burakpadr.decorating.quoting.domain.model.ConsentNotice;
import com.burakpadr.decorating.quoting.domain.model.ConsentNoticeChanged;
import com.burakpadr.decorating.quoting.domain.model.ConsentOutOfOrder;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.ConfirmRoomList;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.in.ReadConsentNotice;
import com.burakpadr.decorating.quoting.domain.port.in.RecordConsent;
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
 * The data-use consent taken on the capture guidance screen (workflow §2.3, BOYA-39).
 *
 * <p>§2.3 asks for one screen carrying two different things: the three shooting rules, and "fotoğrafların
 * ne için kullanılacağı ve ne kadar süre saklanacağı bilgisi burada verilir ve onayı alınır" — what the
 * photographs are used for, how long they are kept, and the consent for it. Only the second half leaves a
 * trace, and this is that trace.
 *
 * <p>Two properties carry the weight, and neither is obvious from the table alone. The version is
 * <em>stamped by the server against the notice the customer actually read</em>, so a grant can always be
 * resolved back to a text (§12: "Consent is versioned so you know which notice each grant referred to").
 * And a refusal is a row, not an absent row — {@code granted boolean} exists so that "said no" and "never
 * reached the screen" stay different facts.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RecordConsentTest {

	@Autowired
	private RecordConsent consents;

	@Autowired
	private ReadConsentNotice notices;

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

	/** A draft that has agreed its room list, which is where §2.3 picks up. */
	private UUID awaitingPhotographs() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, WallCondition.MINOR, null));
		requests.save(draft);
		estimates.estimate(draft.id());
		rooms.confirm(draft.id(), List.of(RoomType.LIVING_ROOM, RoomType.BATHROOM));
		return draft.id();
	}

	@Test
	@DisplayName("§2.3: the notice the screen shows names its own version")
	void publishesTheNoticeWithItsVersion() {
		ConsentNotice notice = notices.current(ConsentType.PROCESSING);

		assertThat(notice.version())
				.as("the version is the filename of the resource that was read, never a client's word for it")
				.isNotBlank();
		assertThat(notice.body())
				.as("§2.3 asks for what the photographs are used for and how long they are kept, so the text "
						+ "has to carry both — §12's answer is thirty days after the request closes")
				.contains("30 gün");
	}

	@Test
	@DisplayName("§12: a grant records which notice it referred to, stamped by the server")
	void stampsTheVersionOfTheNoticeThatWasRead() {
		UUID id = awaitingPhotographs();
		ConsentNotice shown = notices.current(ConsentType.PROCESSING);

		Consent granted = consents.record(id, ConsentType.PROCESSING, true, shown.version());

		assertThat(granted.granted()).isTrue();
		assertThat(granted.type()).isEqualTo(ConsentType.PROCESSING);
		assertThat(granted.textVersion()).isEqualTo(shown.version());
		assertThat(jdbc.queryForObject(
				"SELECT text_version FROM consent WHERE quote_request_id = ? AND consent_type = 'PROCESSING'",
				String.class, id)).isEqualTo(shown.version());
	}

	@Test
	@DisplayName("a refusal is a row, not a missing one")
	void recordsARefusal() {
		UUID id = awaitingPhotographs();
		String version = notices.current(ConsentType.PROCESSING).version();

		Consent refused = consents.record(id, ConsentType.PROCESSING, false, version);

		assertThat(refused.granted()).isFalse();
		assertThat(jdbc.queryForObject(
				"SELECT granted FROM consent WHERE quote_request_id = ?", Boolean.class, id))
				.as("§12 keeps the record; deleting the row would make 'said no' indistinguishable from "
						+ "'never got here'")
				.isFalse();
	}

	@Test
	@DisplayName("acceptance: the answer is the latest decision, and changing one's mind is a new row")
	void keepsEveryDecisionAndAnswersWithTheLatest() {
		UUID id = awaitingPhotographs();
		String version = notices.current(ConsentType.PROCESSING).version();

		consents.record(id, ConsentType.PROCESSING, false, version);
		consents.record(id, ConsentType.PROCESSING, true, version);

		assertThat(consents.latest(id, ConsentType.PROCESSING))
				.get()
				.extracting(Consent::granted)
				.isEqualTo(true);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM consent WHERE quote_request_id = ?", Integer.class, id))
				.as("consent is an append-only log: the schema has no unique key on (request, type) "
						+ "precisely so a re-decision does not overwrite what was decided before")
				.isEqualTo(2);
	}

	@Test
	@DisplayName("a grant against a notice that has since changed is refused, not silently restamped")
	void refusesAGrantAgainstAnOldNotice() {
		UUID id = awaitingPhotographs();

		assertThatThrownBy(() -> consents.record(id, ConsentType.PROCESSING, true, "v0-never-published"))
				.as("stamping the current version onto a grant given against different words would produce "
						+ "exactly the record §12 is trying to prevent: a version that does not say what the "
						+ "customer agreed to")
				.isInstanceOf(ConsentNoticeChanged.class);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM consent WHERE quote_request_id = ?", Integer.class, id)).isZero();
	}

	@Test
	@DisplayName("§2.3 comes after §2.2: consent before there is anything to photograph is out of order")
	void refusesConsentBeforeTheRoomListIsAgreed() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, WallCondition.MINOR, null));
		requests.save(draft);
		String version = notices.current(ConsentType.PROCESSING).version();

		assertThatThrownBy(() -> consents.record(draft.id(), ConsentType.PROCESSING, true, version))
				.isInstanceOf(ConsentOutOfOrder.class);
	}
}
