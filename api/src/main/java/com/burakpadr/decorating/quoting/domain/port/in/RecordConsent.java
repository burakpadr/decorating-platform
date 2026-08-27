package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.Consent;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import java.util.Optional;
import java.util.UUID;

/**
 * The customer's decision on the capture guidance screen (workflow §2.3, BOYA-39).
 *
 * <p>The caller sends the version of the notice it displayed, not the text and not a version it
 * chose: the point of the exchange is that the server can prove which words were on the screen when
 * the box was ticked. A version that is no longer current is refused rather than corrected.
 *
 * @throws com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound if there is no such request
 * @throws com.burakpadr.decorating.quoting.domain.model.ConsentNoticeChanged if the notice moved on
 * @throws com.burakpadr.decorating.quoting.domain.model.ConsentOutOfOrder if the room list is not agreed
 */
public interface RecordConsent {

	Consent record(UUID quoteRequestId, ConsentType type, boolean granted, String textVersion);

	/**
	 * The decision that stands, or empty if the customer never reached the screen.
	 *
	 * <p>The latest of several, because §12 keeps every one: changing one's mind writes a new row and
	 * the schema has no unique key that would stop it.
	 */
	Optional<Consent> latest(UUID quoteRequestId, ConsentType type);
}
