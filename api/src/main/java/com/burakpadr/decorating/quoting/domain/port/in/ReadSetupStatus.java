package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.SetupStatus;

/**
 * What this install still has to be given before it can quote (BOYA-69, workflow §12).
 *
 * <p>§7 does not list it — see {@code docs/decisions/0025}. The panel has to know before it renders
 * anything whether it is looking at a configured system or an empty one, and learning that from a 503
 * on the first price calculation means the operator finds out after typing a job in.
 */
public interface ReadSetupStatus {

	SetupStatus status();
}
