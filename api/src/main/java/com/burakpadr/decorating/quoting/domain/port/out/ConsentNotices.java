package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.ConsentNotice;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;

/**
 * Where the notice texts are read from (decision 0018).
 *
 * <p>A port because the domain must not know that they are files. What matters to it is that the
 * current version is something the application ships, not something the database or an operator can
 * change underneath a grant that already refers to it.
 */
public interface ConsentNotices {

	ConsentNotice current(ConsentType type);
}
