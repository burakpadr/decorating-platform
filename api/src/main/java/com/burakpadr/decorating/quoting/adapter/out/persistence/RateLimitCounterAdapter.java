package com.burakpadr.decorating.quoting.adapter.out.persistence;

import com.burakpadr.decorating.quoting.domain.model.RateBucket;
import com.burakpadr.decorating.quoting.domain.port.out.RateLimiter;
import com.burakpadr.decorating.shared.Uuid7;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code rate_limit_counter} (§4.6, §11).
 *
 * <p>One statement, and that is the point. An upsert that increments and returns in the same round
 * trip has no window between reading a count and writing it for a second request to slip through —
 * which is the failure mode a limit on "the most attackable endpoint in the system" cannot have.
 *
 * <p>{@code REQUIRES_NEW} because the count must survive the caller. An OTP send that is refused rolls
 * back whatever it had started; if the attempt were counted in that same transaction it would roll
 * back too, and the limit would never be reached however often it was tried.
 */
@Component
class RateLimitCounterAdapter implements RateLimiter {

	private final JdbcTemplate jdbc;

	RateLimitCounterAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean tryAcquire(String scopeKey, RateBucket bucket, Duration window, int limit,
			Instant now) {
		Integer count = jdbc.queryForObject("""
				INSERT INTO rate_limit_counter (id, scope_key, bucket, window_start, count)
				VALUES (?, ?, ?, ?, 1)
				ON CONFLICT (scope_key, bucket, window_start)
				DO UPDATE SET count = rate_limit_counter.count + 1
				RETURNING count
				""", Integer.class,
				Uuid7.generate(), scopeKey, bucket.name(), Timestamp.from(windowStart(window, now)));

		return count != null && count <= limit;
	}

	/** The bucket this instant falls in: floor of the epoch against the window length. */
	private static Instant windowStart(Duration window, Instant now) {
		long length = Math.max(1, window.getSeconds());
		return Instant.ofEpochSecond(Math.floorDiv(now.getEpochSecond(), length) * length);
	}
}
