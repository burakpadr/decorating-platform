package com.burakpadr.decorating.quoting.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.RateBucket;
import com.burakpadr.decorating.quoting.domain.port.out.RateLimiter;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The counter behind §11's limits (BOYA-45).
 *
 * <p>§11 is blunt about why this exists on the OTP route: "strict, because every SMS costs money and
 * this is the most attackable endpoint". What it is *not* allowed to be strict about is the IP —
 * "Turkish mobile carriers use CGNAT, thousands of users share an exit IP", so the phone is the
 * primary key of the whole scheme and the IP is a coarse backstop.
 *
 * <p>Fixed windows rather than a rolling count, and the schema chose that: {@code UNIQUE (scope_key,
 * bucket, window_start)} is a bucket per window, and an upsert against it is one statement with no
 * read-then-write in the middle for two requests to interleave inside.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RateLimiterTest {

	@Autowired
	private RateLimiter limiter;

	@Autowired
	private JdbcTemplate jdbc;

	private static final Instant NOON = Instant.parse("2026-09-01T12:00:00Z");

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM rate_limit_counter WHERE scope_key LIKE 'test:%'");
	}

	@Test
	@DisplayName("lets a caller through up to the limit and refuses the one after")
	void countsUpToTheLimit() {
		String key = "test:phone:+905321111111";

		assertThat(limiter.tryAcquire(key, RateBucket.OTP, Duration.ofMinutes(1), 2, NOON)).isTrue();
		assertThat(limiter.tryAcquire(key, RateBucket.OTP, Duration.ofMinutes(1), 2, NOON)).isTrue();
		assertThat(limiter.tryAcquire(key, RateBucket.OTP, Duration.ofMinutes(1), 2, NOON))
				.as("the third in the same minute is the one §11 is written for")
				.isFalse();
	}

	@Test
	@DisplayName("a refused attempt still counts, so hammering does not reset anything")
	void aRefusalIsStillAnAttempt() {
		String key = "test:phone:+905322222222";
		limiter.tryAcquire(key, RateBucket.OTP, Duration.ofMinutes(1), 1, NOON);
		limiter.tryAcquire(key, RateBucket.OTP, Duration.ofMinutes(1), 1, NOON);

		assertThat(jdbc.queryForObject(
				"SELECT count FROM rate_limit_counter WHERE scope_key = ? AND bucket = 'OTP'",
				Integer.class, key)).isEqualTo(2);
	}

	@Test
	@DisplayName("the next window starts again")
	void windowsRollOver() {
		String key = "test:phone:+905323333333";
		limiter.tryAcquire(key, RateBucket.OTP, Duration.ofMinutes(1), 1, NOON);

		assertThat(limiter.tryAcquire(key, RateBucket.OTP, Duration.ofMinutes(1), 1,
				NOON.plus(Duration.ofMinutes(1)))).isTrue();
	}

	@Test
	@DisplayName("a window is a fixed bucket: two calls inside the same minute share it")
	void oneBucketPerWindow() {
		String key = "test:phone:+905324444444";
		limiter.tryAcquire(key, RateBucket.OTP, Duration.ofMinutes(1), 5, NOON);
		limiter.tryAcquire(key, RateBucket.OTP, Duration.ofMinutes(1), 5, NOON.plusSeconds(59));

		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM rate_limit_counter WHERE scope_key = ?", Integer.class, key))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("buckets and keys are counted apart")
	void scopesDoNotShareACount() {
		String phone = "test:phone:+905325555555";
		String ip = "test:ip:1.2.3.4";
		limiter.tryAcquire(phone, RateBucket.OTP, Duration.ofMinutes(1), 1, NOON);

		assertThat(limiter.tryAcquire(ip, RateBucket.OTP, Duration.ofMinutes(1), 1, NOON))
				.as("CGNAT means an IP is thousands of people; it must not inherit one phone's count")
				.isTrue();
		assertThat(limiter.tryAcquire(phone, RateBucket.ANALYSIS, Duration.ofMinutes(1), 1, NOON))
				.isTrue();
	}

	@Test
	@DisplayName("a limit of zero refuses everything, rather than dividing by it")
	void aLimitOfZeroRefuses() {
		assertThat(limiter.tryAcquire("test:phone:+905326666666", RateBucket.OTP,
				Duration.ofMinutes(1), 0, NOON)).isFalse();
	}
}
