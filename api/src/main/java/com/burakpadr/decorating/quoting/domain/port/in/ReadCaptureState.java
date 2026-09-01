package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.CaptureState;
import java.util.UUID;

/**
 * What the capture screen opens on (workflow §2.4, BOYA-42).
 *
 * <p>§7 does not list a read for this and §2.2 did not need one — the list was the answer to
 * confirming it. A screen needs more: it can be opened on a phone that has never seen the list, after
 * the QR handoff or after the customer closed the tab in the hallway, and §10 is explicit that the
 * state for that lives on the server rather than in {@code localStorage} (decision 0015 is the
 * precedent for adding a route §7 does not list).
 *
 * @throws com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound if there is no such request
 */
public interface ReadCaptureState {

	CaptureState of(UUID quoteRequestId);
}
