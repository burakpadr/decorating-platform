package com.burakpadr.decorating.quoting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.shared.Uuid7;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One room, one call, every frame of it labelled (§6, workflow §4.1).
 *
 * <p>The labels are the whole reason this is a domain type and not a list of keys. §6 asks for one call
 * per room so the model can compare walls and count a crack visible in two frames once; the output maps
 * back to a photograph only because each frame went in under a name the response can repeat. A label
 * assigned twice, or assigned in an order the response cannot be read against, loses which wall a
 * finding belongs to — and a finding on the wrong wall is not visibly wrong anywhere.
 */
class RoomAnalysisRequestTest {

	private final UUID roomId = Uuid7.generate();
	private final UUID quoteRequestId = Uuid7.generate();

	@Test
	@DisplayName("the four walls, the ceiling, then the close-ups in the order they were taken")
	void labelsEveryFrame() {
		Photo wall1 = uploaded(PhotoRole.WALL_1, "a1");
		Photo wall2 = uploaded(PhotoRole.WALL_2, "a2");
		Photo ceiling = uploaded(PhotoRole.CEILING, "a3");
		Photo firstCloseUp = uploaded(PhotoRole.DETAIL, "a4");
		Photo secondCloseUp = uploaded(PhotoRole.DETAIL, "a5");

		// Deliberately out of order: rows come back in whatever order the query gave them.
		RoomAnalysisRequest request = RoomAnalysisRequest.of(roomId, RoomType.BEDROOM,
				List.of(secondCloseUp, ceiling, wall2, firstCloseUp, wall1));

		assertThat(request.photos()).extracting(LabelledPhoto::label)
				.containsExactly("WALL_1", "WALL_2", "CEILING", "DETAIL_1", "DETAIL_2");
		assertThat(request.photos()).extracting(LabelledPhoto::photoId)
				.containsExactly(wall1.id(), wall2.id(), ceiling.id(),
						firstCloseUp.id(), secondCloseUp.id());
	}

	@Test
	@DisplayName("the same frames get the same labels however the rows arrive")
	void labelsAreStable() {
		// The property the ordering exists for. A label is what maps a finding back to a wall, and a
		// room re-analysed under a new prompt has to be comparable with the one before it — so
		// "WALL_2 came out dark" must mean the same frame on Tuesday as it did on Monday, whatever
		// order the rows came back in. Chronology is a consequence (UUIDv7 is time-ordered, and two
		// photographs are not taken in the same millisecond); stability is the requirement.
		List<Photo> frames = List.of(uploaded(PhotoRole.WALL_1, "b1"), uploaded(PhotoRole.DETAIL, "b2"),
				uploaded(PhotoRole.CEILING, "b3"), uploaded(PhotoRole.DETAIL, "b4"));

		List<Photo> shuffled = new ArrayList<>(frames);
		Collections.reverse(shuffled);

		assertThat(RoomAnalysisRequest.of(roomId, RoomType.BEDROOM, shuffled).photos())
				.isEqualTo(RoomAnalysisRequest.of(roomId, RoomType.BEDROOM, frames).photos());
	}

	@Test
	@DisplayName("a corner-shot room sends the frames it has, and no placeholders for the rest")
	void labelsOnlyWhatWasCaptured() {
		// A kitchen asks for two frames, not five (workflow §2.4). Padding the call out to WALL_4 would
		// be four labels the response can answer about and nothing behind them.
		RoomAnalysisRequest request = RoomAnalysisRequest.of(roomId, RoomType.KITCHEN,
				List.of(uploaded(PhotoRole.WALL_1), uploaded(PhotoRole.CEILING)));

		assertThat(request.photos()).extracting(LabelledPhoto::label)
				.containsExactly("WALL_1", "CEILING");
	}

	@Test
	@DisplayName("a frame the browser never uploaded is not sent")
	void skipsAnIntentNobodyUsed() {
		// A reserved key with no object behind it. Presigning a read of it produces a URL that 404s, and
		// what the model does with an image it cannot fetch is unspecified — usually it describes the
		// others and says nothing about the missing one, which reads exactly like a wall with no findings.
		Photo reserved = Photo.intended(Uuid7.generate(), quoteRequestId, roomId, PhotoRole.WALL_2);

		RoomAnalysisRequest request = RoomAnalysisRequest.of(roomId, RoomType.BEDROOM,
				List.of(uploaded(PhotoRole.WALL_1), reserved));

		assertThat(request.photos()).extracting(LabelledPhoto::label).containsExactly("WALL_1");
	}

	@Test
	@DisplayName("a room with nothing uploaded is refused rather than asked about")
	void refusesARoomWithNoFrames() {
		// The one case where calling the model is worse than failing: asked about a room it cannot see,
		// a model still answers, and the answer is a plausible average bedroom. That is a priced
		// invention, and nothing downstream can tell it from an observation.
		assertThatThrownBy(() -> RoomAnalysisRequest.of(roomId, RoomType.BEDROOM, List.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(roomId.toString());
	}

	@Test
	@DisplayName("each frame carries the key its object lives under")
	void carriesTheStorageKey() {
		// What the adapter presigns. The bytes never pass through here (§9) — a key does.
		Photo wall = uploaded(PhotoRole.WALL_1);

		RoomAnalysisRequest request =
				RoomAnalysisRequest.of(roomId, RoomType.BEDROOM, List.of(wall));

		assertThat(request.photos()).singleElement()
				.extracting(LabelledPhoto::storageKey)
				.isEqualTo(Photo.keyFor(quoteRequestId, roomId, wall.id()));
	}

	private int nextFrame;

	private Photo uploaded(PhotoRole role) {
		return uploaded(role, "c" + nextFrame++);
	}

	/**
	 * A frame whose id ends in {@code suffix}, so a test can say which close-up was taken first. Ids are
	 * UUIDv7 and therefore time-ordered — but only to the millisecond, and five generated in a loop
	 * share one. Two photographs do not.
	 */
	private Photo uploaded(PhotoRole role, String suffix) {
		UUID id = UUID.fromString("0199c4f2-1c1a-7c3e-9a52-6b1d0f6a" + pad(suffix));
		return Photo.intended(id, quoteRequestId, roomId, role)
				.uploaded(Instant.now(), new CapturedFrame(null, null, null, null, null, false));
	}

	private static String pad(String suffix) {
		return "0".repeat(4 - suffix.length()) + suffix;
	}
}
