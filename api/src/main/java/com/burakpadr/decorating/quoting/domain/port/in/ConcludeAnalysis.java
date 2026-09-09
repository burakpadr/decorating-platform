package com.burakpadr.decorating.quoting.domain.port.in;

import java.util.UUID;

/**
 * The end of stage 4: price what was found and decide what to do about it (workflow §4.2–4.3).
 *
 * <p>Called after every room's analysis lands and does nothing until the last one has. That check is
 * the whole reason this is one use case rather than three calls from a poller: pricing a home whose
 * rooms are still arriving would quote it for the rooms that happened to finish first.
 */
public interface ConcludeAnalysis {

	/** Prices, reviews and moves the request — or returns, if a room is still being analysed. */
	void concludeIfComplete(UUID quoteRequestId);
}
