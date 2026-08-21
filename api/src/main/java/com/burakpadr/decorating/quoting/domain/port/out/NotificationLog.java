package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.TemplateCode;
import com.burakpadr.decorating.shared.PhoneNumber;
import java.util.Optional;
import java.util.UUID;

/**
 * The record of what was sent to whom (§4's {@code notification} table).
 *
 * <p>Written whether or not the provider took it. A message that failed and left no row is a customer
 * saying "you never sent it" and an operator with nothing to check; §13's whole point is that the
 * business can answer that question.
 */
public interface NotificationLog {

	/**
	 * @param providerRef the provider's own id, empty when nothing sent it — which records the row as
	 *     QUEUED rather than SENT
	 */
	void record(UUID quoteRequestId, TemplateCode template, PhoneNumber recipient,
			Optional<String> providerRef);
}
