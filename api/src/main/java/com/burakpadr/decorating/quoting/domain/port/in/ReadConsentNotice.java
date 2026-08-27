package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.ConsentNotice;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;

/**
 * The notice to put in front of the customer (workflow §2.3).
 *
 * <p>Read rather than translated: the words are the artefact the grant refers to, so they are served
 * with the version that names them instead of being duplicated into the frontend's copy file where the
 * two could drift (decision 0018).
 */
public interface ReadConsentNotice {

	ConsentNotice current(ConsentType type);
}
