package com.burakpadr.decorating.quoting.adapter.in.scheduler;

import com.burakpadr.decorating.quoting.domain.model.AnalysisJob;
import com.burakpadr.decorating.quoting.domain.model.UnusableAnalysis;
import com.burakpadr.decorating.quoting.domain.port.in.AnalyseRoom;
import com.burakpadr.decorating.quoting.domain.port.out.AnalysisJobs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * §8's analysis poller: claim a batch every ten seconds and run it.
 *
 * <p>The loop catches per job, which is the ticket's acceptance criterion in one line: a room that
 * fails is a room that is retried, not a home whose remaining rooms are abandoned mid-batch. The catch
 * has to be here rather than inside {@link AnalyseRoom} for a reason worth stating — that call is one
 * transaction, and a transaction which has rolled back cannot be the one that records why.
 *
 * <p>Two kinds of failure, and §8's backoff is only for one of them.
 *
 * <p><b>Nothing was said.</b> A provider that is down, a database that blinked. Waiting is the right
 * answer: {@code 2^attempts} minutes, three attempts, then FAILED with the reason on the row — which
 * is the flag §8 asks for, read by the operator queue (BOYA-53). No notification is sent from here;
 * that is BOYA-64's, and inventing it now would put two mechanisms in front of the same fact.
 *
 * <p><b>Something was said and it cannot be used.</b> §6 already asked again, once, immediately, and
 * the photographs have not changed since. A job-level retry on top of that is two more calls at a
 * model's price for the same frames, so the job fails now. The same goes for a room with no uploaded
 * frame: capture was complete before the request was submitted, so nothing is on its way and six
 * minutes of the customer's promise would be spent waiting for it.
 */
@Component
class AnalysisPoller {

	private static final Logger log = LoggerFactory.getLogger(AnalysisPoller.class);

	private final AnalysisJobs jobs;
	private final AnalyseRoom rooms;
	private final int batchSize;
	private final int maxAttempts;

	AnalysisPoller(AnalysisJobs jobs, AnalyseRoom rooms,
			@Value("${decorating.analysis.batch-size}") int batchSize,
			@Value("${decorating.analysis.max-attempts}") int maxAttempts) {
		this.jobs = jobs;
		this.rooms = rooms;
		this.batchSize = batchSize;
		this.maxAttempts = maxAttempts;
	}

	@Scheduled(fixedDelayString = "${decorating.analysis.poll-interval-ms}")
	void pollForWork() {
		for (AnalysisJob job : jobs.claim(batchSize)) {
			try {
				rooms.analyse(job);
			}
			catch (UnusableAnalysis | IllegalArgumentException permanent) {
				// Nothing another attempt could change: the model has answered twice, or there is
				// nothing to send it.
				log.warn("room {} cannot be analysed, giving up: {}", job.roomId(),
						permanent.getMessage());
				jobs.failed(job.id(), permanent.getMessage());
			}
			catch (RuntimeException transientFailure) {
				retryOrGiveUp(job, transientFailure);
			}
		}
	}

	private void retryOrGiveUp(AnalysisJob job, RuntimeException failure) {
		if (job.exhausted(maxAttempts)) {
			log.warn("room {} failed on attempt {} of {}, leaving it for the operator: {}",
					job.roomId(), job.attempts(), maxAttempts, failure.getMessage());
			jobs.failed(job.id(), failure.getMessage());
			return;
		}
		log.info("room {} failed on attempt {}, retrying in {}: {}",
				job.roomId(), job.attempts(), job.backoff(), failure.getMessage());
		jobs.retryAfter(job.id(), job.backoff(), failure.getMessage());
	}
}
