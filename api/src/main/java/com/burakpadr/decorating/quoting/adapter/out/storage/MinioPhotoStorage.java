package com.burakpadr.decorating.quoting.adapter.out.storage;

import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoStorage;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * MinIO, reached twice from two addresses (§9, BOYA-40).
 *
 * <p>Two clients, and the reason is the deployment rather than the library. The API talks to MinIO
 * over the container network, and the URL it signs is opened by a phone on the customer's mobile
 * connection — the signature covers the host, so a URL signed against {@code http://minio:9000} is a
 * URL that phone cannot use and cannot be repaired afterwards. {@code public-endpoint} is the address
 * the browser will actually reach; {@code endpoint} is ours.
 *
 * <p>No bytes pass through here, by design and by the shape of {@link PhotoStorage}.
 */
@Component
class MinioPhotoStorage implements PhotoStorage {

	private final MinioClient presigner;
	private final MinioClient internal;
	private final String bucket;
	private final Duration putTtl;
	private final Duration getTtl;

	MinioPhotoStorage(
			@Value("${decorating.storage.endpoint}") String endpoint,
			@Value("${decorating.storage.public-endpoint}") String publicEndpoint,
			@Value("${decorating.storage.access-key}") String accessKey,
			@Value("${decorating.storage.secret-key}") String secretKey,
			@Value("${decorating.storage.bucket}") String bucket,
			@Value("${decorating.storage.presigned-put-ttl}") Duration putTtl,
			@Value("${decorating.storage.presigned-get-ttl}") Duration getTtl) {
		this.presigner = client(publicEndpoint, accessKey, secretKey);
		this.internal = client(endpoint, accessKey, secretKey);
		this.bucket = bucket;
		this.putTtl = putTtl;
		this.getTtl = getTtl;
	}

	private static MinioClient client(String endpoint, String accessKey, String secretKey) {
		return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
	}

	@Override
	public PresignedUrl presignPut(String key) {
		return new PresignedUrl(sign(Method.PUT, key, putTtl), putTtl);
	}

	@Override
	public PresignedUrl presignGet(String key) {
		return new PresignedUrl(sign(Method.GET, key, getTtl), getTtl);
	}

	@Override
	public void delete(String key) {
		try {
			internal.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
		}
		catch (Exception failed) {
			throw new StorageUnavailable("could not remove " + key, failed);
		}
	}

	private URI sign(Method method, String key, Duration ttl) {
		try {
			return URI.create(presigner.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
					.method(method)
					.bucket(bucket)
					.object(key)
					.expiry((int) ttl.toSeconds(), TimeUnit.SECONDS)
					.build()));
		}
		catch (Exception failed) {
			// Signing is arithmetic over the key and the secret — it does not call MinIO. Failing here
			// means the configuration is wrong, and answering with a URL nobody can use would move the
			// failure into somebody's phone.
			throw new StorageUnavailable("could not sign a " + method + " for " + key, failed);
		}
	}
}
