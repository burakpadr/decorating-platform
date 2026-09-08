package com.burakpadr.decorating.quoting.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.burakpadr.decorating.shared.Uuid7;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §8's backoff, which is the whole of what a job decides on its own.
 *
 * <p>Worth its own pure test because the two numbers in it are the difference between a provider
 * hiccup that costs a customer nothing and one that costs them the quote. §3.2 promised them an answer
 * inside working hours; three attempts spread over six minutes fits inside that promise, and a backoff
 * that grew faster would spend the promise waiting.
 */
class AnalysisJobTest {

	private static final int MAX_ATTEMPTS = 3;

	@Test
	@DisplayName("2^attempts minutes, counted from the attempt that just failed")
	void backsOffExponentially() {
		// The claim increments attempts before the work runs, so a job asking for its next slot has
		// always been tried at least once: 2 minutes, then 4. §8's arithmetic, and no third wait —
		// the third failure is the last one.
		assertThat(job(1).backoff()).isEqualTo(Duration.ofMinutes(2));
		assertThat(job(2).backoff()).isEqualTo(Duration.ofMinutes(4));
	}

	@Test
	@DisplayName("three attempts and then it is the operator's problem")
	void stopsAfterThreeAttempts() {
		assertThat(job(1).exhausted(MAX_ATTEMPTS)).isFalse();
		assertThat(job(2).exhausted(MAX_ATTEMPTS)).isFalse();
		assertThat(job(3).exhausted(MAX_ATTEMPTS)).isTrue();
	}

	@Test
	@DisplayName("a job that has not been attempted cannot be asking for a retry")
	void refusesToBackOffBeforeItHasRun() {
		// attempts = 0 would answer "one minute" and read like a rule rather than the bug it is: the
		// only way to hold this job is to have claimed it, and claiming counts an attempt.
		assertThatThrownBy(() -> job(0).backoff()).isInstanceOf(IllegalStateException.class);
	}

	private static AnalysisJob job(int attempts) {
		return new AnalysisJob(Uuid7.generate(), Uuid7.generate(), attempts);
	}
}
