package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.AnalysisJob;

/**
 * Analysing one claimed room (§6, §8).
 *
 * <p>All of it or none of it: the findings are written and the job is marked done in one transaction,
 * so there is no state where a room has an analysis and a queue entry still says it needs one — the
 * next tick would hand the same room to a second vision call.
 *
 * <p>It throws rather than swallowing. What to do about a room that did not analyse is §8's policy and
 * it needs a write of its own, outside the transaction that just rolled back — the poller owns that,
 * because a transaction cannot record why it failed from inside itself.
 */
public interface AnalyseRoom {

	void analyse(AnalysisJob job);
}
