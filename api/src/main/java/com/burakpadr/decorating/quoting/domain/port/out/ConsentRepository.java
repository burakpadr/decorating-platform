package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.Consent;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@code consent} rows for a request (§4.7).
 *
 * <p>Append only. There is deliberately no unique key on {@code (quote_request_id, consent_type)} and
 * so no update here either: a customer who changes their mind adds a decision, and §12's record is the
 * sequence of them rather than the last one standing alone.
 */
public interface ConsentRepository {

	void save(Consent consent);

	/** The most recent decision of this type, by {@code created_at}. */
	Optional<Consent> latest(UUID quoteRequestId, ConsentType type);
}
