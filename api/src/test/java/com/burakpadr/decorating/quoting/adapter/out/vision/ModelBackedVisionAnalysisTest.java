package com.burakpadr.decorating.quoting.adapter.out.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.quoting.domain.model.CapturedFrame;
import com.burakpadr.decorating.quoting.domain.model.Coating;
import com.burakpadr.decorating.quoting.domain.model.CrackLevel;
import com.burakpadr.decorating.quoting.domain.model.FillerBand;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysisRequest;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.SurfaceFinding;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import com.burakpadr.decorating.quoting.domain.model.UnusableAnalysis;
import com.burakpadr.decorating.quoting.domain.model.VisionUnavailable;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoStorage;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The provider-independent half of the vision adapter (§6, §8, BOYA-47).
 *
 * <p>Everything that is not "which provider" lives here and is tested here: the prompt and the schema
 * that go out, the presigned reads the frames go out as, the one retry §6 allows, and the mapping of a
 * validated response onto findings. {@link VisionModel} is the seam the provider goes behind, and it
 * is a scripted double in this file — which is the point of the seam, and the reason a suite covering
 * the analysis path costs nothing to run.
 *
 * <p>The two assertions that matter most are about failure. §6 allows exactly one retry of an
 * unusable answer, and no retry at all of an outage — an outage is the job's backoff to handle (§8),
 * and retrying a timeout inside a call multiplies the wait by two before the job has waited once.
 * Nothing partial ever comes back: a response that does not validate produces an exception, never a
 * {@link RoomAnalysis} with the fields that happened to parse.
 */
class ModelBackedVisionAnalysisTest {

	private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

	private final UUID roomId = UUID.fromString("0199c4f2-1c1a-7c3e-9a52-6b1d0f6a1a00");
	private final UUID quoteRequestId = UUID.fromString("0199c4f2-1c1a-7c3e-9a52-6b1d0f6a1aff");

	private final ScriptedModel model = new ScriptedModel();
	private final RoomAnalysisPrompt prompts = new RoomAnalysisPrompt();
	private final RoomAnalysisSchema schema = new RoomAnalysisSchema();
	private final PhotoStorage storage =
			new StubStorage(key -> new PresignedUrl(URI.create("https://minio.test/" + key), FIVE_MINUTES));

	private final ModelBackedVisionAnalysis vision =
			new ModelBackedVisionAnalysis(model, prompts, schema, storage);

	// -----------------------------------------------------------------------------------------------
	// What goes out
	// -----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("one call carries every frame of the room, close-ups included, each under its label")
	void sendsTheWholeRoomInOneCall() {
		// §6's reason for the whole arrangement: a crack visible in WALL_2 and again in DETAIL_1 is one
		// crack, and only a model holding both frames at once can say so.
		model.willAnswer(valid());

		vision.analyse(request(PhotoRole.WALL_1, PhotoRole.WALL_2, PhotoRole.CEILING, PhotoRole.DETAIL));

		assertThat(model.calls).hasSize(1);
		assertThat(model.calls.getFirst().images()).extracting(VisionImage::label)
				.containsExactly("WALL_1", "WALL_2", "CEILING", "DETAIL_1");
	}

	@Test
	@DisplayName("the frames go as short-lived presigned reads, not as bytes")
	void sendsPresignedReads() {
		model.willAnswer(valid());

		vision.analyse(request(PhotoRole.WALL_1));

		assertThat(model.calls.getFirst().images()).singleElement()
				.extracting(image -> image.read().url().toString(), image -> image.read().expiresIn())
				.containsExactly("https://minio.test/" + key(PhotoRole.WALL_1), FIVE_MINUTES);
	}

	@Test
	@DisplayName("the prompt and the schema sent are the files on disk, not a copy of them")
	void sendsTheVersionedAssets() {
		model.willAnswer(valid());

		vision.analyse(request(PhotoRole.WALL_1));

		assertThat(model.calls.getFirst().instructions()).isEqualTo(prompts.text());
		assertThat(model.calls.getFirst().responseSchema()).isEqualTo(schema.asJson());
	}

	// -----------------------------------------------------------------------------------------------
	// What comes back
	// -----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("a validated response becomes findings, attributed to the prompt and the model")
	void mapsAValidatedResponse() {
		model.willAnswer(valid());

		RoomAnalysis analysis = vision.analyse(request(PhotoRole.WALL_1));

		assertThat(analysis.roomId()).isEqualTo(roomId);
		assertThat(analysis.promptVersion()).isEqualTo(prompts.version());
		assertThat(analysis.modelVersion()).isEqualTo("scripted-model-1");
		assertThat(analysis.rawResponse()).isEqualTo(valid());

		assertThat(analysis.furnishing()).isEqualTo(Furnishing.FURNISHED);
		assertThat(analysis.doorCount()).isEqualTo(2);
		assertThat(analysis.windowCount()).isEqualTo(3);
		assertThat(analysis.radiatorCount()).isEqualTo(1);
		assertThat(analysis.cornice()).isTrue();
		assertThat(analysis.downlightCount()).isEqualTo(6);
		assertThat(analysis.reportedConfidence()).isEqualByComparingTo("0.83");
		// The ceiling's own reading, which §6 leaves out of "surfaces" and decision 0021 puts back into
		// the room's confidence — the ceiling is priced, so how well it was read counts.
		assertThat(analysis.ceilingConfidence()).isEqualByComparingTo("0.79");
		assertThat(analysis.unusablePhotos()).isEmpty();
		assertThat(analysis.notes()).containsExactly("sol duvarda priz hizasında çatlak");

		assertThat(analysis.surfaces()).singleElement().isEqualTo(new SurfaceFinding(
				"WALL_1", Coating.PAINTED, Tone.DARK, FillerBand.MEDIUM, false,
				CrackLevel.HAIRLINE, Moisture.NONE, false, new BigDecimal("0.88")));
	}

	@Test
	@DisplayName("a dry ceiling stain is priced; a wet one is the finding that stops the job")
	void mapsTheCeilingOntoTheRiskPredicate() {
		// ADR 0017 put the predicate on CeilingFinding so BOYA-51 has one place to ask. Decision 0020
		// gave the model a word that can make it true — before that, ACTIVE was unreachable and a leaking
		// ceiling priced itself.
		model.willAnswer(valid());
		assertThat(vision.analyse(request(PhotoRole.WALL_1)).ceiling())
				.returns(Moisture.STAIN, ceiling -> ceiling.staining())
				.returns(FillerBand.LOW, ceiling -> ceiling.filler())
				.returns(false, ceiling -> ceiling.isRisk());

		model.willAnswer(valid().replace("\"staining\": \"STAIN\"", "\"staining\": \"ACTIVE\""));
		assertThat(vision.analyse(request(PhotoRole.WALL_1)).ceiling().isRisk()).isTrue();
	}

	@Test
	@DisplayName("the labels of the frames the model could not use come back")
	void carriesTheUnusableFrames() {
		// What BOYA-51 turns into RECAPTURE and BOYA-61 turns into "the second wall came out dark".
		model.willAnswer(valid().replace("\"unusablePhotos\": []", "\"unusablePhotos\": [\"WALL_2\"]"));

		assertThat(vision.analyse(request(PhotoRole.WALL_1)).unusablePhotos()).containsExactly("WALL_2");
	}

	// -----------------------------------------------------------------------------------------------
	// Failure
	// -----------------------------------------------------------------------------------------------

	@Test
	@DisplayName("an unusable answer is asked again exactly once")
	void retriesAnUnusableAnswerOnce() {
		model.willAnswer("Elbette! İşte oda analizi:", valid());

		RoomAnalysis analysis = vision.analyse(request(PhotoRole.WALL_1));

		assertThat(model.calls).hasSize(2);
		assertThat(analysis.surfaces()).hasSize(1);
	}

	@Test
	@DisplayName("unusable twice fails the job, and says what was wrong with it")
	void failsAfterTheSecondUnusableAnswer() {
		String noConfidence = valid().replace("\"confidence\": 0.83,", "");
		model.willAnswer(noConfidence, noConfidence);

		assertThatThrownBy(() -> vision.analyse(request(PhotoRole.WALL_1)))
				.isInstanceOf(UnusableAnalysis.class)
				.hasMessageContaining("confidence")
				.hasMessageContaining(roomId.toString());

		// Twice, not three times. A third call is a third bill and a third chance at the same answer.
		assertThat(model.calls).hasSize(2);
	}

	@Test
	@DisplayName("a provider that is down is not retried here — that is the job's backoff")
	void doesNotRetryAnOutage() {
		// §8 retries a failed analysis_job with 2^attempts minutes between tries. A retry inside the call
		// would double an outage before the job has waited once, and would spend a second timeout doing
		// it — while the row still says RUNNING.
		model.willFail(new VisionUnavailable("connect timed out"));

		assertThatThrownBy(() -> vision.analyse(request(PhotoRole.WALL_1)))
				.isInstanceOf(VisionUnavailable.class);

		assertThat(model.calls).hasSize(1);
	}

	@Test
	@DisplayName("nothing partial comes back: a response missing one surface field yields no analysis")
	void neverReturnsHalfAnAnalysis() {
		// The failure this forbids is the quiet one. A response whose surfaces parsed and whose ceiling
		// did not would produce a room with sound walls overhead of nothing — priced, plausible, and
		// wrong in a direction nobody looks.
		String noCeiling = valid().replaceAll("(?s)\"ceiling\": \\{.*?\\},", "");
		model.willAnswer(noCeiling, noCeiling);

		assertThatThrownBy(() -> vision.analyse(request(PhotoRole.WALL_1)))
				.isInstanceOf(UnusableAnalysis.class)
				.hasMessageContaining("ceiling");
	}

	// -----------------------------------------------------------------------------------------------

	private RoomAnalysisRequest request(PhotoRole... roles) {
		List<Photo> photos = new ArrayList<>();
		for (PhotoRole role : roles) {
			photos.add(Photo.intended(photoIdOf(role), quoteRequestId, roomId, role)
					.uploaded(Instant.now(), new CapturedFrame(null, null, null, null, null, false)));
		}
		return RoomAnalysisRequest.of(roomId, RoomType.LIVING_ROOM, photos);
	}

	/** Stable per role, so the expected storage key can be written down. */
	private static UUID photoIdOf(PhotoRole role) {
		return UUID.fromString("0199c4f2-1c1a-7c3e-9a52-6b1d0f6a1a1" + role.ordinal());
	}

	private String key(PhotoRole role) {
		return Photo.keyFor(quoteRequestId, roomId, photoIdOf(role));
	}

	private static String valid() {
		return RoomAnalysisSchemaTest.example();
	}

	private static final class ScriptedModel implements VisionModel {

		private final Deque<Object> answers = new ArrayDeque<>();
		private final List<VisionPrompt> calls = new ArrayList<>();

		void willAnswer(String... texts) {
			answers.clear();
			for (String text : texts) {
				answers.add(text);
			}
		}

		void willFail(RuntimeException failure) {
			answers.clear();
			answers.add(failure);
		}

		@Override
		public VisionCompletion complete(VisionPrompt prompt) {
			calls.add(prompt);
			Object answer = answers.size() > 1 ? answers.poll() : answers.peek();
			if (answer instanceof RuntimeException failure) {
				throw failure;
			}
			return new VisionCompletion((String) answer, "scripted-model-1");
		}
	}

	private record StubStorage(java.util.function.Function<String, PresignedUrl> get)
			implements PhotoStorage {

		@Override
		public PresignedUrl presignPut(String key) {
			throw new UnsupportedOperationException("the vision adapter never writes");
		}

		@Override
		public PresignedUrl presignGet(String key) {
			return get.apply(key);
		}

		@Override
		public void delete(String key) {
			throw new UnsupportedOperationException("the vision adapter never deletes");
		}
	}
}
