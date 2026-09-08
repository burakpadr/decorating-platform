package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.AnalysisJob;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The analysis queue (§8).
 *
 * <p>No broker: a table, claimed with {@code FOR UPDATE SKIP LOCKED}, which is what makes two
 * instances polling the same row safe. The alternative at this scale is a second piece of
 * infrastructure to deploy, monitor and lose messages in.
 *
 * <p>Four ways out of a claim, and they are not interchangeable. {@link #done} is the answer arriving.
 * {@link #retryAfter} is a wait §8 sizes at {@code 2^attempts} minutes. {@link #failed} is the third
 * attempt spent, which leaves the row where an operator will see it — {@code FAILED} with a reason is
 * the flag, and inventing a notification here would be BOYA-64's job done in the wrong place.
 */
public interface AnalysisJobs {

	/** One job per room, the moment a request enters ANALYZING. */
	void enqueueFor(List<UUID> roomIds);

	/**
	 * Takes up to {@code limit} due jobs and counts an attempt against each.
	 *
	 * <p>Claiming is what makes a job this instance's, so the count belongs to the claim rather than to
	 * the work: a crash between the two would otherwise leave a row that had run and did not say so,
	 * and it would run forever.
	 */
	List<AnalysisJob> claim(int limit);

	void done(UUID jobId);

	void retryAfter(UUID jobId, Duration delay, String error);

	void failed(UUID jobId, String error);
}
