package com.burakpadr.decorating.quoting.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The direct-to-storage upload, against a real MinIO (§9, BOYA-40).
 *
 * <p>This is the ticket's acceptance criterion and it cannot be checked with a mock: "fotoğraf JVM'den
 * geçmez" is a claim about a URL a browser uses, and the browser's first request is a CORS preflight
 * that never reaches our code. Without {@code PUT} and {@code OPTIONS} allowed from the web origin the
 * whole capture flow fails — silently, in somebody else's browser, after eight minutes of their work.
 *
 * <p>So MinIO here is started the way the compose files start it, and the test reads that
 * configuration out of them rather than restating it: where a comment would ask two things to stay in
 * step, a test does the asking ({@code districts.spec.ts} is the pattern).
 *
 * <p>The rule is set on the server, not on the bucket. MinIO answers {@code PutBucketCors} with "not
 * implemented", so {@code mc cors set} — the command this repository documented for production until
 * this test was written — cannot ever have worked: CORS is {@code MINIO_API_CORS_ALLOW_ORIGIN} and
 * nothing else.
 */
@Testcontainers
class MinioPhotoStorageTest {

	private static final String BUCKET = "decorating-photos";
	private static final String WEB_ORIGIN = "http://localhost:3000";
	private static final Path COMPOSE = Path.of("..", "infra", "docker-compose.dev.yml");
	private static final Path PRODUCTION_COMPOSE = Path.of("..", "infra", "docker-compose.yml");

	private static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:latest")
			.withCommand("server", "/data", "--console-address", ":9001")
			.withEnv("MINIO_ROOT_USER", "minioadmin")
			.withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
			// The one setting the capture flow depends on, and the compose files set the same one.
			.withEnv("MINIO_API_CORS_ALLOW_ORIGIN", WEB_ORIGIN)
			.withExposedPorts(9000)
			.waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

	private static MinioPhotoStorage storage;

	@BeforeAll
	static void startAndConfigureTheBucket() throws Exception {
		MINIO.start();
		// The two commands minio-init runs, and only those.
		exec("mc", "alias", "set", "local", "http://localhost:9000", "minioadmin", "minioadmin");
		exec("mc", "mb", "--ignore-existing", "local/" + BUCKET);

		String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
		storage = new MinioPhotoStorage(endpoint, endpoint, "minioadmin", "minioadmin", BUCKET,
				Duration.ofMinutes(15), Duration.ofMinutes(5));
	}

	private static HttpResponse<Void> send(HttpRequest.Builder request) throws Exception {
		return HttpClient.newHttpClient().send(request.build(), BodyHandlers.discarding());
	}

	private static void exec(String... command) throws Exception {
		var result = MINIO.execInContainer(command);
		assertThat(result.getExitCode())
				.as("%s → %s%s", String.join(" ", command), result.getStdout(), result.getStderr())
				.isZero();
	}

	@Test
	@DisplayName("acceptance: the browser can PUT the photograph itself, and the JVM never sees it")
	void aPresignedPutStoresTheObject() throws Exception {
		PresignedUrl upload = storage.presignPut("quotes/a/b/c.jpg");

		HttpResponse<Void> put = send(HttpRequest.newBuilder(upload.url())
				.header("Content-Type", "image/jpeg")
				.header("Origin", WEB_ORIGIN)
				.PUT(BodyPublishers.ofByteArray("not really a jpeg".getBytes(StandardCharsets.UTF_8))));

		assertThat(put.statusCode()).isEqualTo(200);
		// Sent by the browser, straight to storage: no request ever reached the API with these bytes in
		// it, which is the whole of §9's first sentence.
		assertThat(put.headers().firstValue("access-control-allow-origin")).contains(WEB_ORIGIN);
	}

	@Test
	@DisplayName("acceptance: the preflight the browser sends first is allowed for PUT")
	void thePreflightAllowsPut() throws Exception {
		PresignedUrl upload = storage.presignPut("quotes/a/b/preflight.jpg");

		HttpResponse<Void> preflight = send(HttpRequest.newBuilder(upload.url())
				.header("Origin", WEB_ORIGIN)
				.header("Access-Control-Request-Method", "PUT")
				.header("Access-Control-Request-Headers", "content-type")
				.method("OPTIONS", BodyPublishers.noBody()));

		// A browser that gets anything but a pass here never sends the PUT at all, and the failure
		// surfaces as an upload that silently does nothing.
		assertThat(preflight.statusCode()).isBetween(200, 204);
		assertThat(preflight.headers().firstValue("access-control-allow-origin")).contains(WEB_ORIGIN);
		assertThat(preflight.headers().firstValue("access-control-allow-methods").orElse(""))
				.contains("PUT");
	}

	@Test
	@DisplayName("and refused for any other origin, because the default is not a rule")
	void thePreflightRefusesAnyOtherOrigin() throws Exception {
		PresignedUrl upload = storage.presignPut("quotes/a/b/elsewhere.jpg");

		HttpResponse<Void> preflight = send(HttpRequest.newBuilder(upload.url())
				.header("Origin", "https://not-ours.example")
				.header("Access-Control-Request-Method", "PUT")
				.method("OPTIONS", BodyPublishers.noBody()));

		// Left unset, MinIO answers every origin — so a page anywhere could use a presigned URL the
		// customer's own session was handed. This is the assertion that fails when the setting is
		// missing; the one above passes either way, because a permissive server allows our origin too.
		assertThat(preflight.headers().firstValue("access-control-allow-origin"))
				.isNotEqualTo(java.util.Optional.of("https://not-ours.example"));
	}

	@Test
	@DisplayName("the operator's read is presigned too, and short-lived")
	void aPresignedGetReadsItBack() throws Exception {
		String key = "quotes/a/b/read-back.jpg";
		send(HttpRequest.newBuilder(storage.presignPut(key).url())
				.PUT(BodyPublishers.ofString("bytes")));

		PresignedUrl read = storage.presignGet(key);
		HttpResponse<String> got = HttpClient.newHttpClient()
				.send(HttpRequest.newBuilder(read.url()).GET().build(), BodyHandlers.ofString());

		assertThat(got.statusCode()).isEqualTo(200);
		assertThat(got.body()).isEqualTo("bytes");
		// §9: operator reads only, short TTL. A link that outlives the screen it was made for is a link
		// that can be forwarded.
		assertThat(read.expiresIn()).isLessThanOrEqualTo(Duration.ofMinutes(5));
	}

	@Test
	@DisplayName("an unsigned request reads nothing: the bucket is not public")
	void thereIsNoUnsignedRead() throws Exception {
		String key = "quotes/a/b/private.jpg";
		send(HttpRequest.newBuilder(storage.presignPut(key).url()).PUT(BodyPublishers.ofString("x")));

		URI unsigned = URI.create(
				"http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000) + "/" + BUCKET + "/" + key);
		HttpResponse<Void> got = send(HttpRequest.newBuilder(unsigned).GET());

		assertThat(got.statusCode()).isEqualTo(403);
	}

	@Test
	@DisplayName("a retake removes the object, not just the row")
	void deleteRemovesTheObject() throws Exception {
		String key = "quotes/a/b/retaken.jpg";
		send(HttpRequest.newBuilder(storage.presignPut(key).url()).PUT(BodyPublishers.ofString("x")));

		storage.delete(key);

		HttpResponse<Void> gone = send(HttpRequest.newBuilder(storage.presignGet(key).url()).GET());
		// Otherwise the object outlives the request, is never named by a row again, and PhotoPurge
		// cannot find it either — §12's retention counted from a row nobody kept.
		assertThat(gone.statusCode()).isEqualTo(404);
	}

	@Test
	@DisplayName("deleting an object that was never uploaded is not an error")
	void deletingWhatWasNeverThereIsQuiet() {
		// The intent whose PUT never happened is the common case: the customer closed the tab. Cleaning
		// up after it must not be the thing that fails the retake.
		storage.delete("quotes/a/b/never-arrived.jpg");
	}

	@Test
	@DisplayName("the CORS setting this test relies on is the one the compose files set")
	void theComposeFilesSetTheSameRule() throws IOException {
		String compose = Files.readString(COMPOSE) + Files.readString(PRODUCTION_COMPOSE);

		// The rule lives in the compose file, and this test would happily pass against a bucket
		// configured only here — which is how "works on my machine" is spelled in infrastructure.
		assertThat(compose)
				.as("without this the browser's preflight fails and every upload silently does nothing")
				.contains("MINIO_API_CORS_ALLOW_ORIGIN");
		// And not by accident on the default, which is "*": a storage server that answers any origin is
		// one a page on any domain can be pointed at with a link the customer's own session signed.
		assertThat(compose).doesNotContain("MINIO_API_CORS_ALLOW_ORIGIN: \"*\"");
	}
}
