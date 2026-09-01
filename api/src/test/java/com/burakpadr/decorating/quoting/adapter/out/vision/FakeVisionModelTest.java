package com.burakpadr.decorating.quoting.adapter.out.vision;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.quoting.domain.model.CapturedFrame;
import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysisRequest;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoStorage;
import com.burakpadr.decorating.shared.Uuid7;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The fake, held to the same schema as the provider that has not been chosen (BOYA-47).
 *
 * <p>A fake is only worth having if the code it stands in for cannot tell the difference, and the
 * assertion that buys that is the first one here: everything it produces validates against the real
 * {@code schema.json}. Without it a fake drifts, the suite stays green on responses no provider would
 * send, and the drift surfaces on the first real call — in production, against a customer's
 * photographs.
 *
 * <p>It is driven through {@link ModelBackedVisionAnalysis} rather than called directly, because that
 * is how it will be used: the point of faking at the model seam rather than at the port is that the
 * whole production path runs over it.
 */
class FakeVisionModelTest {

	private final RoomAnalysisSchema schema = new RoomAnalysisSchema();
	private final FakeVisionModel fake = new FakeVisionModel();
	private final ModelBackedVisionAnalysis vision =
			new ModelBackedVisionAnalysis(fake, new RoomAnalysisPrompt(), schema, new StubStorage());

	@Test
	@DisplayName("every room it invents validates against the real schema")
	void alwaysAnswersInSchema() {
		// Twenty rooms of three shapes. Any invalid response would surface here as UnusableAnalysis,
		// because the adapter validates the fake exactly as it would validate a provider.
		for (int room = 0; room < 20; room++) {
			assertThat(analyse(fourWalls())).isNotNull();
			assertThat(analyse(cornerShots())).isNotNull();
			assertThat(analyse(fourWallsAndCloseUps())).isNotNull();
		}
	}

	@Test
	@DisplayName("a corner-shot room gets one ROOM_GENERAL surface; a walled room gets one per wall")
	void followsTheCaptureShape() {
		assertThat(analyse(cornerShots()).surfaces()).singleElement()
				.extracting(surface -> surface.surfaceId()).isEqualTo("ROOM_GENERAL");

		assertThat(analyse(fourWalls()).surfaces()).extracting(surface -> surface.surfaceId())
				.containsExactly("WALL_1", "WALL_2", "WALL_3", "WALL_4");
	}

	@Test
	@DisplayName("the same room analysed twice says the same thing")
	void isDeterministic() {
		// A presigned URL is signed afresh every call, so a fake keyed on the whole URL would answer
		// differently on the retry — and an analysis that changes when nothing changed is a fake nobody
		// can debug against.
		RoomAnalysisRequest room = fourWalls();

		assertThat(vision.analyse(room).rawResponse()).isEqualTo(vision.analyse(room).rawResponse());
	}

	@Test
	@DisplayName("different rooms get different findings")
	void variesByRoom() {
		List<String> responses = new ArrayList<>();
		for (int room = 0; room < 12; room++) {
			responses.add(analyse(fourWalls()).rawResponse());
		}

		assertThat(responses).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("a row it produced says so, for as long as the row exists")
	void admitsWhatItIs() {
		// model_version is §4.4's audit trail. A finding invented by a fake and a finding observed by a
		// provider are the same shape on the row; this is the only thing that tells them apart.
		assertThat(analyse(fourWalls()).modelVersion()).isEqualTo("fake");
	}

	private RoomAnalysis analyse(RoomAnalysisRequest request) {
		return vision.analyse(request);
	}

	private static RoomAnalysisRequest fourWalls() {
		return room(PhotoRole.WALL_1, PhotoRole.WALL_2, PhotoRole.WALL_3, PhotoRole.WALL_4,
				PhotoRole.CEILING);
	}

	private static RoomAnalysisRequest fourWallsAndCloseUps() {
		return room(PhotoRole.WALL_1, PhotoRole.WALL_2, PhotoRole.WALL_3, PhotoRole.WALL_4,
				PhotoRole.CEILING, PhotoRole.DETAIL, PhotoRole.DETAIL);
	}

	private static RoomAnalysisRequest cornerShots() {
		return room(PhotoRole.CEILING);
	}

	private static RoomAnalysisRequest room(PhotoRole... roles) {
		UUID quoteRequestId = Uuid7.generate();
		UUID roomId = Uuid7.generate();
		List<Photo> photos = new ArrayList<>();
		for (PhotoRole role : roles) {
			photos.add(Photo.intended(Uuid7.generate(), quoteRequestId, roomId, role)
					.uploaded(Instant.now(), new CapturedFrame(null, null, null, null, null, false)));
		}
		return RoomAnalysisRequest.of(roomId, RoomType.BEDROOM, photos);
	}

	/** Signs a different URL every call, the way a real one does. */
	private static final class StubStorage implements PhotoStorage {

		@Override
		public PresignedUrl presignPut(String key) {
			throw new UnsupportedOperationException();
		}

		@Override
		public PresignedUrl presignGet(String key) {
			return new PresignedUrl(
					URI.create("https://minio.test/" + key + "?signature=" + Uuid7.generate()),
					Duration.ofMinutes(5));
		}

		@Override
		public void delete(String key) {
			throw new UnsupportedOperationException();
		}
	}
}
