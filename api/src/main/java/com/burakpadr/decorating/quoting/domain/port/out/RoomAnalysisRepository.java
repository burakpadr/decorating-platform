package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where an analysis is kept (§4.4).
 *
 * <p>Findings go in and findings come out. Nothing above this port parses JSON to learn what a room
 * looks like — that is §4.4's rule and the reason {@code surface_finding} exists beside
 * {@code raw_response}: the engine reads rows, and calibration is a plain SQL join.
 *
 * <p>{@code room_analysis.room_id} is UNIQUE, so a room has one analysis and saving again replaces it.
 * That is what a recapture wants (the frames it complained about are gone), and what it costs is the
 * superseded reading — recorded instead by {@code quote_request.recapture_count}, which is the fact
 * anybody actually asks for.
 */
public interface RoomAnalysisRepository {

	void save(RoomAnalysis analysis);

	Optional<RoomAnalysis> findByRoom(UUID roomId);

	/** Every analysed room of one request, in the order the customer photographs them. */
	List<RoomAnalysis> findByQuoteRequest(UUID quoteRequestId);
}
