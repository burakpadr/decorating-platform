package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.config.session.NotYourQuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.CapturedFrame;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoNotFound;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.PhotoUploadIntent;
import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.CapturePhotos;
import com.burakpadr.decorating.quoting.domain.port.in.ConfirmRoomList;
import com.burakpadr.decorating.quoting.domain.port.in.ReadConsentNotice;
import com.burakpadr.decorating.quoting.domain.port.in.RecordConsent;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoStorage;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Reserving, completing and retaking a frame (§9, workflow §2.4–2.7, BOYA-40).
 *
 * <p>Storage is mocked here on purpose: what a presigned URL does is {@code MinioPhotoStorageTest}'s
 * subject, against a real MinIO. What is left is the part that decides whose photograph this is and
 * whether it may exist at all — and every one of those rules is about a row nobody can see from the
 * outside, which is exactly where a mistake stays invisible until an operator opens somebody else's
 * bathroom.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CapturePhotosTest {

	@Autowired
	private CapturePhotos photos;

	@Autowired
	private ConfirmRoomList roomList;

	@Autowired
	private RecordConsent consents;

	@Autowired
	private ReadConsentNotice notices;

	@Autowired
	private EstimateStageOne estimates;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private PhotoStorage storage;

	@BeforeEach
	void storageSignsWhateverItIsAsked() {
		given(storage.presignPut(anyString()))
				.willReturn(new PresignedUrl(URI.create("http://storage.test/put"), Duration.ofMinutes(15)));
	}

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	/** A request that has agreed its room list, which is where §2.4 picks up. */
	private ConfirmedRooms capturing() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 6, false, WallCondition.GOOD, null));
		requests.save(draft);
		estimates.estimate(draft.id());
		ConfirmedRooms agreed = roomList.confirm(draft.id(), List.of(
				RoomType.LIVING_ROOM, RoomType.MASTER_BEDROOM, RoomType.BEDROOM,
				RoomType.KITCHEN, RoomType.BATHROOM, RoomType.HALLWAY));
		// §2.3 is now a rule and not only a screen: PhotoCaptureService refuses to reserve a frame for a
		// request that has not agreed the notice, so the fixture has to agree it like a customer would.
		consents.record(draft.id(), ConsentType.PROCESSING, true,
				notices.current(ConsentType.PROCESSING).version());
		return agreed;
	}

	private UUID requestOf(ConfirmedRooms rooms) {
		return jdbc.queryForObject("SELECT quote_request_id FROM room WHERE id = ?", UUID.class,
				rooms.rooms().getFirst().id());
	}

	@Test
	@DisplayName("an intent reserves §9's key and hands back a URL the browser uses directly")
	void intentReservesAKeyAndSignsAUrl() {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		UUID room = rooms.rooms().getFirst().id();

		PhotoUploadIntent intent = photos.intend(request, room, PhotoRole.WALL_1);

		assertThat(intent.photo().storageKey())
				.isEqualTo("quotes/" + request + "/" + room + "/" + intent.photo().id() + ".jpg");
		assertThat(intent.upload().url()).hasToString("http://storage.test/put");
		Map<String, Object> row = jdbc.queryForMap(
				"SELECT * FROM photo WHERE id = ?", intent.photo().id());
		assertThat(row.get("role")).isEqualTo("WALL_1");
		assertThat(row.get("uploaded_at"))
				.as("the row exists before the photograph does — it is what the completion has to find")
				.isNull();
	}

	@Test
	@DisplayName("asking twice for the same frame replaces the reservation nobody used")
	void asecondIntentSupersedesAnUnusedOne() {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		UUID room = rooms.rooms().getFirst().id();
		PhotoUploadIntent first = photos.intend(request, room, PhotoRole.WALL_1);

		PhotoUploadIntent second = photos.intend(request, room, PhotoRole.WALL_1);

		// The upload that never happened is the ordinary case: a lift with no signal, a closed tab. If
		// the second attempt collided with the first, the customer would be stuck on that frame with no
		// way forward that they could find.
		assertThat(second.photo().id()).isNotEqualTo(first.photo().id());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM photo WHERE room_id = ? AND role = 'WALL_1'",
				Integer.class, room)).isOne();
		verify(storage).delete(first.photo().storageKey());
	}

	@Test
	@DisplayName("a frame already uploaded is not quietly overwritten")
	void refusesToReplaceAnUploadedFrame() {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		UUID room = rooms.rooms().getFirst().id();
		PhotoUploadIntent taken = photos.intend(request, room, PhotoRole.WALL_1);
		photos.complete(request, taken.photo().id(), new CapturedFrame(
				null, 2048, 1536, 300_000, new BigDecimal("70"), false));

		assertThatThrownBy(() -> photos.intend(request, room, PhotoRole.WALL_1))
				.as("retaking is DELETE then a new intent (§7): a silent overwrite would leave the old "
						+ "object in the bucket with nothing naming it")
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("§2.6: close-ups repeat as often as there are cracks")
	void detailFramesAreUnlimited() {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		UUID room = rooms.rooms().getFirst().id();

		photos.intend(request, room, PhotoRole.DETAIL);
		photos.intend(request, room, PhotoRole.DETAIL);
		photos.intend(request, room, PhotoRole.DETAIL);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM photo WHERE room_id = ? AND role = 'DETAIL'",
				Integer.class, room)).isEqualTo(3);
	}

	@Test
	@DisplayName("a room belonging to somebody else is refused, whatever the id looks like")
	void refusesARoomFromAnotherRequest() {
		ConfirmedRooms mine = capturing();
		ConfirmedRooms theirs = capturing();
		UUID myRequest = requestOf(mine);
		UUID theirRoom = theirs.rooms().getFirst().id();

		assertThatThrownBy(() -> photos.intend(myRequest, theirRoom, PhotoRole.WALL_1))
				.isInstanceOf(NotYourQuoteRequest.class);
	}

	@Test
	@DisplayName("§3: nothing is photographed before the list is agreed")
	void refusesARequestStillInDraft() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate());
		requests.save(draft);

		assertThatThrownBy(() -> photos.intend(draft.id(), Uuid7.generate(), PhotoRole.WALL_1))
				// A photograph against a request with no agreed list has no room to belong to, and the
				// analysis would be reading frames of a home nobody described.
				.isInstanceOf(RuntimeException.class);
	}

	@Test
	@DisplayName("completing records what the client measured on the way out")
	void completingStoresTheMeasurements() {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		PhotoUploadIntent intent = photos.intend(request, rooms.rooms().getFirst().id(), PhotoRole.CEILING);

		Photo done = photos.complete(request, intent.photo().id(), new CapturedFrame(
				Instant.parse("2026-08-24T07:30:00Z"), 2048, 1152, 388_120,
				new BigDecimal("41.55"), true));

		assertThat(done.isUploaded()).isTrue();
		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM photo WHERE id = ?", intent.photo().id());
		assertThat(row.get("uploaded_at")).isNotNull();
		assertThat(row.get("captured_at")).isNotNull();
		assertThat(row.get("width")).isEqualTo(2048);
		assertThat((BigDecimal) row.get("quality_score")).isEqualByComparingTo("41.55");
		// §9 accepts the third attempt at a frame rather than fighting the customer over it, and the
		// flag is how the operator knows to look at it twice.
		assertThat(row.get("low_quality_flag")).isEqualTo(true);
	}

	@Test
	@DisplayName("completing somebody else's photograph is refused")
	void refusesToCompleteAnotherRequestsPhoto() {
		ConfirmedRooms mine = capturing();
		ConfirmedRooms theirs = capturing();
		PhotoUploadIntent theirPhoto =
				photos.intend(requestOf(theirs), theirs.rooms().getFirst().id(), PhotoRole.WALL_2);
		UUID myRequest = requestOf(mine);

		assertThatThrownBy(() -> photos.complete(myRequest, theirPhoto.photo().id(),
				new CapturedFrame(null, 2048, 1536, 300_000, null, false)))
				.isInstanceOf(NotYourQuoteRequest.class);
	}

	@Test
	@DisplayName("a retake removes the row and the object together")
	void discardRemovesBoth() {
		ConfirmedRooms rooms = capturing();
		UUID request = requestOf(rooms);
		PhotoUploadIntent intent = photos.intend(request, rooms.rooms().getFirst().id(), PhotoRole.WALL_3);
		photos.complete(request, intent.photo().id(),
				new CapturedFrame(null, 2048, 1536, 300_000, null, false));

		photos.discard(request, intent.photo().id());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM photo WHERE id = ?", Integer.class,
				intent.photo().id())).isZero();
		// The object first, then the row: an object nothing names is invisible to PhotoPurge as well,
		// and §12's retention is counted from rows.
		verify(storage).delete(intent.photo().storageKey());
	}

	@Test
	@DisplayName("a photograph that does not exist is not found rather than half-deleted")
	void unknownPhotoIsNotFound() {
		ConfirmedRooms rooms = capturing();

		assertThatThrownBy(() -> photos.discard(requestOf(rooms), Uuid7.generate()))
				.isInstanceOf(PhotoNotFound.class);
	}
}
