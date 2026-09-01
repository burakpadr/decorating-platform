package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.CaptureState;
import com.burakpadr.decorating.quoting.domain.model.CapturedFrame;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.PhotoUploadIntent;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.CapturePhotos;
import com.burakpadr.decorating.quoting.domain.port.in.ConfirmRoomList;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.in.ReadCaptureState;
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
 * Where a capture has got to (workflow §2.4, BOYA-42).
 *
 * <p>§7 lists no way to read this back, and until now nothing needed one: the room list was returned
 * once, as the answer to confirming it. A capture screen cannot work from that. It has to open on a
 * phone that has never seen the list — §10's own rule is that state lives on the server because "people
 * abandon mid-flow and resume on another device", and the desktop-to-mobile handoff is precisely how
 * this screen is usually reached.
 *
 * <p>The distinction that matters most here is between a frame that was reserved and a frame that
 * arrived. An upload intent is a signed URL and a promise; a lift with no signal breaks the promise and
 * leaves the row behind. A screen that counted reservations would tell the customer they had finished
 * while the bucket was half empty.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReadCaptureStateTest {

	@Autowired
	private ReadCaptureState capture;

	@Autowired
	private CapturePhotos photos;

	@Autowired
	private ConfirmRoomList rooms;

	@Autowired
	private EstimateStageOne estimates;

	@Autowired
	private RecordConsent consents;

	@Autowired
	private ReadConsentNotice notices;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	private record Capturing(UUID id, ConfirmedRooms areas) {}

	/** A 2+1 with its list agreed and the notice accepted, which is where §2.4 begins. */
	private Capturing capturing() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 3, false, WallCondition.MINOR, null));
		requests.save(draft);
		estimates.estimate(draft.id());
		ConfirmedRooms agreed = rooms.confirm(draft.id(),
				List.of(RoomType.LIVING_ROOM, RoomType.BATHROOM));
		consents.record(draft.id(), ConsentType.PROCESSING, true,
				notices.current(ConsentType.PROCESSING).version());
		return new Capturing(draft.id(), agreed);
	}

	private UUID roomOf(Capturing session, int index) {
		return session.areas().rooms().get(index).id();
	}

	/** Reserve and complete one frame, as the browser does either side of its PUT. */
	private UUID upload(Capturing session, UUID roomId, PhotoRole role) {
		PhotoUploadIntent intent = photos.intend(session.id(), roomId, role);
		photos.complete(session.id(), intent.photo().id(),
				new CapturedFrame(null, 2048, 1536, 240_000, new BigDecimal("96.10"), false));
		return intent.photo().id();
	}

	@Test
	@DisplayName("§2.4: the areas come back in capture order, each with the frames it asks for")
	void listsTheAgreedAreasAndTheirFrames() {
		Capturing session = capturing();

		CaptureState state = capture.of(session.id());

		assertThat(state.areas()).extracting("label").containsExactly("Salon", "Banyo");
		assertThat(state.areas().get(0).frames()).extracting("role")
				.as("§2.4's table: four walls and the ceiling for a living room")
				.containsExactly(PhotoRole.WALL_1, PhotoRole.WALL_2, PhotoRole.WALL_3,
						PhotoRole.WALL_4, PhotoRole.CEILING);
		assertThat(state.areas().get(1).frames()).extracting("role")
				.as("a bathroom is mostly tile and cupboard, so one general frame and the ceiling")
				.containsExactly(PhotoRole.WALL_1, PhotoRole.CEILING);
	}

	@Test
	@DisplayName("a frame nobody has photographed yet is not taken")
	void reportsAnUntouchedFrameAsOutstanding() {
		Capturing session = capturing();

		CaptureState state = capture.of(session.id());

		assertThat(state.areas().get(0).frames().get(0).taken()).isFalse();
		assertThat(state.areas().get(0).frames().get(0).photoId()).isNull();
		assertThat(state.taken()).isZero();
		assertThat(state.required()).isEqualTo(7);
		assertThat(state.complete()).isFalse();
	}

	@Test
	@DisplayName("a frame that arrived is taken, and names the photo so a retake can replace it")
	void reportsAnUploadedFrame() {
		Capturing session = capturing();
		UUID photoId = upload(session, roomOf(session, 0), PhotoRole.WALL_1);

		CaptureState state = capture.of(session.id());

		assertThat(state.areas().get(0).frames().get(0).taken()).isTrue();
		assertThat(state.areas().get(0).frames().get(0).photoId()).isEqualTo(photoId);
		assertThat(state.taken()).isEqualTo(1);
	}

	@Test
	@DisplayName("acceptance: a reservation nobody uploaded does not count as a photograph")
	void doesNotCountAnIntentThatNeverArrived() {
		Capturing session = capturing();
		photos.intend(session.id(), roomOf(session, 0), PhotoRole.WALL_2);

		CaptureState state = capture.of(session.id());

		assertThat(state.areas().get(0).frames().get(1).taken())
				.as("an intent is a signed URL and a promise; a lift with no signal breaks it, and a "
						+ "screen that counted promises would say the capture was done")
				.isFalse();
		assertThat(state.taken()).isZero();
	}

	@Test
	@DisplayName("a frame kept despite its score says so, so the screen need not re-measure it")
	void carriesTheLowQualityFlag() {
		Capturing session = capturing();
		PhotoUploadIntent intent = photos.intend(session.id(), roomOf(session, 1), PhotoRole.CEILING);
		photos.complete(session.id(), intent.photo().id(),
				new CapturedFrame(null, 2048, 1536, 90_000, new BigDecimal("3.20"), true));

		CaptureState state = capture.of(session.id());

		assertThat(state.areas().get(1).frames().get(1).lowQualityFlag()).isTrue();
	}

	@Test
	@DisplayName("an area is complete when every frame it asks for has arrived, and so is the capture")
	void reportsCompletion() {
		Capturing session = capturing();
		UUID bathroom = roomOf(session, 1);
		upload(session, bathroom, PhotoRole.WALL_1);
		upload(session, bathroom, PhotoRole.CEILING);

		CaptureState state = capture.of(session.id());

		assertThat(state.areas().get(1).complete()).isTrue();
		assertThat(state.areas().get(0).complete()).isFalse();
		assertThat(state.complete())
				.as("§3's submit arrow is guarded on every required frame being in, not on most of them")
				.isFalse();

		UUID living = roomOf(session, 0);
		for (PhotoRole role : List.of(PhotoRole.WALL_1, PhotoRole.WALL_2, PhotoRole.WALL_3,
				PhotoRole.WALL_4, PhotoRole.CEILING)) {
			upload(session, living, role);
		}

		assertThat(capture.of(session.id()).complete()).isTrue();
	}

	@Test
	@DisplayName("a request whose list is not agreed yet has nothing to photograph")
	void isEmptyBeforeTheListIsAgreed() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 3, false, WallCondition.MINOR, null));
		requests.save(draft);

		CaptureState state = capture.of(draft.id());

		assertThat(state.areas()).isEmpty();
		assertThat(state.complete())
				.as("nothing required and nothing taken is not a finished capture")
				.isFalse();
	}

	@Test
	@DisplayName("there is no state for a request that does not exist")
	void refusesAnUnknownRequest() {
		assertThatThrownBy(() -> capture.of(Uuid7.generate()))
				.isInstanceOf(QuoteRequestNotFound.class);
	}
}
