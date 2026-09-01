package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.RateBucket;
import java.time.Duration;
import java.time.Instant;

/**
 * §11's counter: how many times this key has done this thing in this window.
 *
 * <p>Fixed windows, not a rolling count. The schema chose it — {@code UNIQUE (scope_key, bucket,
 * window_start)} is exactly one row per bucket per window — and it is the right trade here: a rolling
 * count needs a row per event and a scan to answer, where this is one upsert that returns the answer.
 * The cost is that a caller can spend a whole window's allowance at its very end and another at the
 * start of the next. For "one SMS a minute" that is two SMS in two seconds, once, which is a long way
 * from the failure §11 is guarding against.
 *
 * <p>The limits themselves are not here. They belong to configuration and differ per bucket and per
 * scope, and a port that knew them could not be told about a new one without being changed.
 */
public interface RateLimiter {

	/**
	 * Records an attempt and says whether it was within the limit.
	 *
	 * <p>The attempt is counted either way. A refusal that did not count would let a caller reset the
	 * window by hammering it, which is the opposite of what a limit is for.
	 *
	 * @param scopeKey {@code "phone:+9053..."} or {@code "ip:1.2.3.4"} — §4.6 fixes the shape
	 * @return false when this attempt is the one over the line
	 */
	boolean tryAcquire(String scopeKey, RateBucket bucket, Duration window, int limit, Instant now);
}
