package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.AnalysisJob;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.CapturedFrame;
import com.burakpadr.decorating.quoting.domain.model.CeilingFinding;
import com.burakpadr.decorating.quoting.domain.model.Coating;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms;
import com.burakpadr.decorating.quoting.domain.model.ConfirmedRooms.ConfirmedRoom;
import com.burakpadr.decorating.quoting.domain.model.CrackLevel;
import com.burakpadr.decorating.quoting.domain.model.FillerBand;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysisRequest;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.SurfaceFinding;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import com.burakpadr.decorating.quoting.domain.model.VisionUnavailable;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.AnalyseRoom;
import com.burakpadr.decorating.quoting.domain.port.out.AnalysisJobs;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoRepository;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomAnalysisRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import com.burakpadr.decorating.quoting.domain.port.out.VisionAnalysisPort;
import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Analysing a claimed room, against a real database and a stubbed model (BOYA-48).
 *
 * <p>Vision is mocked here on purpose: what a provider answers is
 * {@code ModelBackedVisionAnalysisTest}'s subject. What is left is the property that needs rows to
 * check at all — the findings and the closed job are one transaction, so a room never ends up
 * analysed with a job still asking for it. That state costs a second provider call and overwrites the
 * analysis the first one paid for, and nothing about the row would look wrong.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AnalyseRoomTest {

	@Autowired
	private AnalyseRoom analyseRoom;

	@MockitoSpyBean
	private AnalysisJobs jobs;

	@Autowired
	private RoomAnalysisRepository analyses;

	@Autowired
	private RoomRepository rooms;

	@Autowired
	private PhotoRepository photos;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private VisionAnalysisPort vision;

	private UUID roomId;
	private UUID quoteRequestId;

	@BeforeEach
	void aRoomWithItsFramesUploadedAndQueued() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.TWO_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.EMPTY, 3, false, WallCondition.MINOR, null));
		requests.save(draft);
		quoteRequestId = draft.id();

		roomId = Uuid7.generate();
		rooms.replaceAll(quoteRequestId, new ConfirmedRooms(List.of(new ConfirmedRoom(
				roomId, RoomType.BEDROOM, "Yatak odası", 0,
				List.of(PhotoRole.WALL_1, PhotoRole.CEILING), true))));

		upload(PhotoRole.WALL_1);
		upload(PhotoRole.CEILING);
		jobs.enqueueFor(List.of(roomId));
	}

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	@Test
	@DisplayName("the room's own frames go to the model, and the findings and the closed job land together")
	void analysesTheRoomAndClosesItsJob() {
		AnalysisJob job = jobs.claim(1).getFirst();
		given(vision.analyse(any(RoomAnalysisRequest.class))).willReturn(findings());

		analyseRoom.analyse(job);

		assertThat(analyses.findByRoom(roomId)).isPresent();
		assertThat(jdbc.queryForObject("SELECT status FROM analysis_job WHERE id = ?",
				String.class, job.id())).isEqualTo("DONE");
	}

	@Test
	@DisplayName("a room the model could not be reached about is left exactly as it was")
	void writesNothingWhenTheModelCannotBeReached() {
		// The transaction rolls back and the row goes back to the queue untouched — RUNNING, because
		// deciding what happens next is the poller's, in a write of its own.
		AnalysisJob job = jobs.claim(1).getFirst();
		willThrow(new VisionUnavailable("connect timed out"))
				.given(vision).analyse(any(RoomAnalysisRequest.class));

		assertThatThrownBy(() -> analyseRoom.analyse(job)).isInstanceOf(VisionUnavailable.class);

		assertThat(analyses.findByRoom(roomId)).isEmpty();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM room_analysis", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT status FROM analysis_job WHERE id = ?",
				String.class, job.id())).isEqualTo("RUNNING");
	}

	@Test
	@DisplayName("findings the job could not be closed against are not kept")
	void rollsBackTheFindingsIfTheJobCannotBeClosed() {
		// The other half of the pair, and the expensive one. A written analysis with a job still asking
		// for it means the next tick pays for the same room again and overwrites what it already bought.
		// Spied rather than stubbed, because everything up to this point has to be the real thing.
		AnalysisJob job = jobs.claim(1).getFirst();
		given(vision.analyse(any(RoomAnalysisRequest.class))).willReturn(findings());
		willThrow(new IllegalStateException("connection reset")).given(jobs).done(job.id());

		assertThatThrownBy(() -> analyseRoom.analyse(job)).isInstanceOf(IllegalStateException.class);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM room_analysis", Integer.class)).isZero();
	}

	@Test
	@DisplayName("only this room's frames are sent, close-ups and all")
	void sendsOneRoomsFrames() {
		// §6's call is one room. A second room's frames in the same context is a model comparing walls
		// from two different rooms and counting one crack twice.
		UUID otherRoom = Uuid7.generate();
		rooms.replaceAll(quoteRequestId, new ConfirmedRooms(List.of(
				new ConfirmedRoom(roomId, RoomType.BEDROOM, "Yatak odası", 0,
						List.of(PhotoRole.WALL_1, PhotoRole.CEILING), true),
				new ConfirmedRoom(otherRoom, RoomType.KITCHEN, "Mutfak", 1,
						List.of(PhotoRole.WALL_1), true))));
		upload(PhotoRole.WALL_1);
		upload(PhotoRole.CEILING);
		photos.save(Photo.intended(Uuid7.generate(), quoteRequestId, otherRoom, PhotoRole.WALL_1)
				.uploaded(Instant.now(), new CapturedFrame(null, null, null, null, null, false)));
		upload(PhotoRole.DETAIL);

		jdbc.update("DELETE FROM analysis_job");
		jobs.enqueueFor(List.of(roomId));
		AnalysisJob job = jobs.claim(1).getFirst();
		given(vision.analyse(any(RoomAnalysisRequest.class))).willReturn(findings());

		analyseRoom.analyse(job);

		org.mockito.ArgumentCaptor<RoomAnalysisRequest> sent =
				org.mockito.ArgumentCaptor.forClass(RoomAnalysisRequest.class);
		org.mockito.Mockito.verify(vision).analyse(sent.capture());
		assertThat(sent.getValue().roomId()).isEqualTo(roomId);
		assertThat(sent.getValue().photos()).extracting(photo -> photo.label())
				.containsExactly("WALL_1", "CEILING", "DETAIL_1");
	}

	// -----------------------------------------------------------------------------------------------

	private void upload(PhotoRole role) {
		photos.save(Photo.intended(Uuid7.generate(), quoteRequestId, roomId, role)
				.uploaded(Instant.now(), new CapturedFrame(null, null, null, null, null, false)));
	}

	private RoomAnalysis findings() {
		return new RoomAnalysis(roomId, "v1", "test-model", "{}",
				List.of(new SurfaceFinding("WALL_1", Coating.PAINTED, Tone.LIGHT, FillerBand.NONE,
						false, CrackLevel.NONE, Moisture.NONE, false, new BigDecimal("0.900"))),
				CeilingFinding.none(), new BigDecimal("0.800"), false, 0,
				Furnishing.EMPTY, 1, 1, 1, new BigDecimal("0.850"), List.of(), List.of());
	}
}
