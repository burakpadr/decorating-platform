package com.burakpadr.decorating.quoting.domain.model;

import java.time.Duration;
import java.util.UUID;

/**
 * One room's place in the analysis queue ({@code analysis_job}, §8).
 *
 * <p>A job is a room, because §6's call is a room: seven for a 3+1 home, and a failure retries the one
 * room it happened in rather than the house. That is the acceptance criterion of the poller and it is
 * decided here, by what a row is.
 *
 * <p>{@code attempts} is the count including the one currently running — §8's claim increments it as
 * it takes the row, so a job in hand has always been tried at least once. The backoff reads from that
 * number, so the two have to be understood together or the first retry waits a minute instead of two.
 */
public record AnalysisJob(UUID id, UUID roomId, int attempts) {

	public AnalysisJob {
		if (id == null || roomId == null) {
			throw new IllegalArgumentException("a job is a row and a room");
		}
		if (attempts < 0) {
			throw new IllegalArgumentException("attempts cannot be negative: " + attempts);
		}
	}

	/**
	 * §8's wait before the next attempt: {@code 2^attempts} minutes.
	 *
	 * <p>Two minutes, then four. Short on purpose — §3.2 has already told the customer when their quote
	 * will be ready, so the retries have to fit inside a promise somebody is waiting on rather than
	 * inside a provider's worst day.
	 */
	public Duration backoff() {
		if (attempts == 0) {
			// Not a rule with a value: reaching here means a job was rescheduled without being claimed,
			// and answering "one minute" would bury that.
			throw new IllegalStateException("job " + id + " has not been attempted, so it is not retrying");
		}
		return Duration.ofMinutes(1L << attempts);
	}

	/** Whether §8's third attempt has been used up, leaving the row for the operator to look at. */
	public boolean exhausted(int maxAttempts) {
		return attempts >= maxAttempts;
	}
}
