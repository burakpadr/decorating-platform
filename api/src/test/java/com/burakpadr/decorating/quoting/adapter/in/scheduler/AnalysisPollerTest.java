package com.burakpadr.decorating.quoting.adapter.in.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.quoting.domain.model.AnalysisJob;
import com.burakpadr.decorating.quoting.domain.model.UnusableAnalysis;
import com.burakpadr.decorating.quoting.domain.model.VisionUnavailable;
import com.burakpadr.decorating.quoting.domain.port.in.AnalyseRoom;
import com.burakpadr.decorating.quoting.domain.port.out.AnalysisJobs;
import com.burakpadr.decorating.shared.Uuid7;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §8's policy: what happens to a job that did not finish (BOYA-48).
 *
 * <p>Driven with stubs and no Spring, because none of what is asserted here is about the database —
 * {@code AnalysisJobPersistenceAdapterTest} owns the claim. What is here is the set of decisions the
 * poller makes, and each one is a different customer's afternoon.
 *
 * <p>The first test is the ticket's acceptance criterion, and it is about a loop: "if one room's
 * analysis fails, only that room is retried, not the whole analysis". A batch that abandons its
 * remaining rooms because the second one threw would leave four rooms of a home unanalysed with
 * nothing on any row explaining why, and the next tick would re-run the ones that already worked.
 */
class AnalysisPollerTest {

	private static final int MAX_ATTEMPTS = 3;
	private static final int BATCH = 5;

	private final Queue queue = new Queue();
	private final Map<UUID, Consumer<AnalysisJob>> behaviour = new HashMap<>();
	private final List<UUID> attempted = new ArrayList<>();

	/**
	 * Marking a job done is not in here, and that is deliberate: {@code RoomAnalysisService} does it
	 * inside the transaction that writes the findings. Split across two transactions, a crash between
	 * them would leave an analysed room with a job still asking for it — a second call at a model's
	 * price, overwriting the analysis it already paid for.
	 */
	private final AnalysisPoller poller = new AnalysisPoller(queue, job -> {
		attempted.add(job.roomId());
		behaviour.getOrDefault(job.roomId(), ignored -> { }).accept(job);
	}, BATCH, MAX_ATTEMPTS);

	@Test
	@DisplayName("one room failing does not abandon the rest of the batch")
	void carriesOnAfterAFailedRoom() {
		AnalysisJob first = queued();
		AnalysisJob broken = queued();
		AnalysisJob last = queued();
		behaviour.put(broken.roomId(), job -> {
			throw new VisionUnavailable("connect timed out");
		});

		poller.pollForWork();

		assertThat(attempted).containsExactly(first.roomId(), broken.roomId(), last.roomId());
		assertThat(queue.rescheduled).containsOnlyKeys(broken.id());
		assertThat(queue.failed).isEmpty();
	}

	@Test
	@DisplayName("an answer that did not validate fails the job — §6 already spent its retry")
	void doesNotRetryAnUnusableAnswer() {
		// The adapter asks again once, immediately, and then gives up (§6). A job-level retry on top of
		// that is two more calls at a model's price for the same photographs, which have not changed.
		AnalysisJob job = queued();
		behaviour.put(job.roomId(), ignored -> {
			throw new UnusableAnalysis("room came back unusable", List.of("required 'ceiling' missing"));
		});

		poller.pollForWork();

		assertThat(queue.rescheduled).isEmpty();
		assertThat(queue.failed).containsOnlyKeys(job.id());
		assertThat(queue.failed.get(job.id())).contains("ceiling");
	}

	@Test
	@DisplayName("a provider that is down is waited out, 2^attempts minutes")
	void reschedulesAnOutage() {
		AnalysisJob job = queued();
		behaviour.put(job.roomId(), ignored -> {
			throw new VisionUnavailable("connect timed out");
		});

		poller.pollForWork();

		assertThat(queue.rescheduled).containsEntry(job.id(), Duration.ofMinutes(2));
		assertThat(queue.failed).isEmpty();
	}

	@Test
	@DisplayName("the third attempt is the last, and the row keeps the reason")
	void failsAfterTheThirdAttempt() {
		// §8's max_attempts. FAILED with last_error is the flag the operator queue reads (BOYA-53) —
		// a row that stopped without saying why is a room somebody has to re-derive.
		AnalysisJob job = queued(MAX_ATTEMPTS);
		behaviour.put(job.roomId(), ignored -> {
			throw new VisionUnavailable("connect timed out");
		});

		poller.pollForWork();

		assertThat(queue.rescheduled).isEmpty();
		assertThat(queue.failed.get(job.id())).contains("connect timed out");
	}

	@Test
	@DisplayName("a room with nothing to send is not tried three times")
	void failsImmediatelyWhenThereIsNothingToAnalyse() {
		// RoomAnalysisRequest refuses a room whose frames never arrived, and no wait fixes that: capture
		// was complete before the request was submitted, so nothing is on its way. Backing off twice
		// would spend six minutes of a promise on a job that cannot change its mind.
		AnalysisJob job = queued();
		behaviour.put(job.roomId(), ignored -> {
			throw new IllegalArgumentException("no frame of room " + job.roomId() + " has been uploaded");
		});

		poller.pollForWork();

		assertThat(queue.rescheduled).isEmpty();
		assertThat(queue.failed).containsOnlyKeys(job.id());
	}

	@Test
	@DisplayName("an empty queue is a tick that touches nothing")
	void doesNothingWithoutWork() {
		poller.pollForWork();

		assertThat(queue.claims).containsExactly(BATCH);
		assertThat(attempted).isEmpty();
	}

	// -----------------------------------------------------------------------------------------------

	private AnalysisJob queued() {
		return queued(1);
	}

	private AnalysisJob queued(int attempts) {
		AnalysisJob job = new AnalysisJob(Uuid7.generate(), Uuid7.generate(), attempts);
		queue.pending.add(job);
		return job;
	}

	/** Records what the poller did to each job, which is the whole of what this class decides. */
	private static final class Queue implements AnalysisJobs {

		private final List<AnalysisJob> pending = new ArrayList<>();
		private final List<Integer> claims = new ArrayList<>();
		private final Map<UUID, Duration> rescheduled = new HashMap<>();
		private final Map<UUID, String> failed = new HashMap<>();

		@Override
		public void enqueueFor(List<UUID> roomIds) {
			throw new UnsupportedOperationException("the poller never queues work");
		}

		@Override
		public List<AnalysisJob> claim(int limit) {
			claims.add(limit);
			List<AnalysisJob> batch = List.copyOf(pending.subList(0, Math.min(limit, pending.size())));
			pending.clear();
			return batch;
		}

		@Override
		public void done(UUID jobId) {
			throw new UnsupportedOperationException("the use case closes its own job, in its transaction");
		}

		@Override
		public void retryAfter(UUID jobId, Duration delay, String error) {
			rescheduled.put(jobId, delay);
		}

		@Override
		public void failed(UUID jobId, String error) {
			failed.put(jobId, error);
		}
	}
}
