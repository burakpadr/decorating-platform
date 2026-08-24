package com.burakpadr.decorating.quoting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.shared.Uuid7;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One photograph, from the intent that reserves it to the row that says it arrived (§9, BOYA-40).
 *
 * <p>The photograph itself never passes through the JVM: the browser uploads it straight to storage
 * with a presigned URL. What this type holds is everything the server does know — where the object
 * lives, which frame of which room it is, and what the client measured before sending it.
 */
class PhotoTest {

	private static final UUID REQUEST = Uuid7.generate();
	private static final UUID ROOM = Uuid7.generate();

	@Test
	@DisplayName("§9: the key is the request, the room and the photo, and nothing guessable")
	void buildsTheKeySpecifiedInSection9() {
		UUID photo = Uuid7.generate();

		String key = Photo.keyFor(REQUEST, ROOM, photo);

		assertThat(key).isEqualTo("quotes/" + REQUEST + "/" + ROOM + "/" + photo + ".jpg");
	}

	@Test
	@DisplayName("an intent is a reservation: a key and a role, and no photograph yet")
	void anIntentIsNotAnUpload() {
		Photo intent = Photo.intended(Uuid7.generate(), REQUEST, ROOM, PhotoRole.WALL_1);

		assertThat(intent.isUploaded())
				.as("the browser has been handed a URL and may never use it — a row that claimed the "
						+ "photograph had arrived would be a room the analysis is asked to read blind")
				.isFalse();
		assertThat(intent.storageKey()).endsWith(intent.id() + ".jpg");
		assertThat(intent.lowQualityFlag()).isFalse();
	}

	@Test
	@DisplayName("completing records what the client measured, and when it arrived")
	void completingRecordsTheMeasurements() {
		Photo intent = Photo.intended(Uuid7.generate(), REQUEST, ROOM, PhotoRole.CEILING);
		Instant taken = Instant.parse("2026-08-24T09:12:00Z");
		Instant arrived = Instant.parse("2026-08-24T09:12:41Z");

		Photo uploaded = intent.uploaded(arrived, new CapturedFrame(
				taken, 2048, 1536, 412_233, new BigDecimal("84.10"), false));

		assertThat(uploaded.isUploaded()).isTrue();
		assertThat(uploaded.uploadedAt()).isEqualTo(arrived);
		// EXIF DateTimeOriginal, read before the re-encode strips it (§9). Not the upload time: the two
		// differ by however long the customer spent in the room, and the analysis reads the first.
		assertThat(uploaded.capturedAt()).isEqualTo(taken);
		assertThat(uploaded.width()).isEqualTo(2048);
		assertThat(uploaded.storageKey()).isEqualTo(intent.storageKey());
		assertThat(uploaded.id()).isEqualTo(intent.id());
	}

	@Test
	@DisplayName("a variance too large for the column is clamped, never rejected")
	void clampsAQualityScoreTheColumnCannotHold() {
		Photo intent = Photo.intended(Uuid7.generate(), REQUEST, ROOM, PhotoRole.WALL_2);

		Photo uploaded = intent.uploaded(Instant.now(), new CapturedFrame(
				null, 2048, 1536, 400_000, new BigDecimal("2481.77"), false));

		// Laplacian variance has no upper bound and quality_score is numeric(5,2). A sharp photograph
		// is exactly the one that overflows it, so the sharpest frames would be the ones that failed to
		// save — §9's rule the other way round.
		assertThat(uploaded.qualityScore()).isEqualByComparingTo("999.99");
	}

	@Test
	@DisplayName("a frame with no measurements is still a frame")
	void acceptsAFrameTheClientCouldNotMeasure() {
		Photo intent = Photo.intended(Uuid7.generate(), REQUEST, ROOM, PhotoRole.DETAIL);

		Photo uploaded = intent.uploaded(Instant.now(), new CapturedFrame(
				null, null, null, null, null, true));

		// An old phone with no EXIF and a canvas that would not measure is not a reason to lose the
		// photograph. The flag is what the operator reads; §9 would rather accept a mediocre frame.
		assertThat(uploaded.isUploaded()).isTrue();
		assertThat(uploaded.capturedAt()).isNull();
		assertThat(uploaded.lowQualityFlag()).isTrue();
	}

	@Test
	@DisplayName("a negative measurement is a client bug, and is refused rather than stored")
	void refusesImpossibleMeasurements() {
		Photo intent = Photo.intended(Uuid7.generate(), REQUEST, ROOM, PhotoRole.WALL_3);

		assertThatThrownBy(() -> intent.uploaded(Instant.now(), new CapturedFrame(
				null, -1, 1536, 400_000, null, false)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> intent.uploaded(Instant.now(), new CapturedFrame(
				null, 2048, 1536, 400_000, new BigDecimal("-3"), false)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("§4.3: only the close-up may repeat within a room")
	void onlyTheDetailFrameRepeats() {
		assertThat(PhotoRole.DETAIL.isRepeatable()).isTrue();
		// The other five name a wall or the ceiling. Two rows for WALL_1 would be two answers to "what
		// does this wall look like", and the analysis has no way to choose between them.
		assertThat(PhotoRole.WALL_1.isRepeatable()).isFalse();
		assertThat(PhotoRole.CEILING.isRepeatable()).isFalse();
	}
}
