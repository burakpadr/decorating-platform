package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * Where quote requests live (§4.2).
 *
 * <p>{@code save} is an upsert of the whole aggregate rather than a patch. The answers were merged in
 * the domain — {@code StageOneAnswers.mergedWith} — so by the time it reaches here the request is
 * already what it should become, and a column-by-column update would be that merge written a second
 * time in SQL, free to disagree with the first.
 */
public interface QuoteRequestRepository {

	void save(QuoteRequest request);

	Optional<QuoteRequest> findById(UUID id);
}
