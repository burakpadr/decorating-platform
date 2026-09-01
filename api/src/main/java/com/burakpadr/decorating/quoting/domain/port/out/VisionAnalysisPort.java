package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysisRequest;
import com.burakpadr.decorating.quoting.domain.model.UnusableAnalysis;
import com.burakpadr.decorating.quoting.domain.model.VisionUnavailable;

/**
 * Asking a model what one room looks like (§6, workflow §4.1).
 *
 * <p>This port exists in this shape for a testing reason before an architectural one: a suite that
 * makes real vision calls is unusable — slow, priced per run, and non-deterministic in the one part of
 * the system whose output is money — so nothing above this line may know that a provider exists.
 * Everything that decides a price is driven through it with findings that a test wrote.
 *
 * <p>One call, one room. The granularity is §6's and it is why {@code analysis_job} is keyed by room:
 * a failure retries the room it happened in.
 *
 * <p>Two failures, kept apart because §8 handles them differently. {@link VisionUnavailable} means
 * nothing was said and waiting is the right answer, so the job's backoff takes it. {@link
 * UnusableAnalysis} means something was said and it did not validate — §6 allows one immediate retry,
 * which happens inside the adapter, and then the job fails for the operator to look at.
 *
 * <p>Nothing partial is ever returned. An implementation either produces a {@link RoomAnalysis} whose
 * every field came from one validated response, or it throws. A half-mapped analysis is a room
 * described by a model that was interrupted, and there is no field on the row that says so.
 */
public interface VisionAnalysisPort {

	RoomAnalysis analyse(RoomAnalysisRequest request);
}
