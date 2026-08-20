package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.DuplicateVersionCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionNotFound;
import com.burakpadr.decorating.quoting.domain.port.in.ManagePriceBookVersions;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookVersionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The price book management use case (§7, workflow §6).
 *
 * <p>Thin on purpose: the rules that matter here are the ones the database enforces — one active
 * version, unique codes, a copy that either lands whole or not at all — and restating them in Java
 * would only create a second place for them to be true. What this class owns is the order of
 * operations and the failures the operator sees.
 */
@Service
@Transactional
class PriceBookVersionService implements ManagePriceBookVersions {

	private final PriceBookVersionRepository versions;

	PriceBookVersionService(PriceBookVersionRepository versions) {
		this.versions = versions;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PriceBookSummary> list() {
		return versions.findAll();
	}

	@Override
	public PriceBookSummary createVersionFrom(UUID sourceId, String versionCode) {
		// Checked before the copy so a rejected request writes nothing, and so the operator is told
		// which of the two things was wrong rather than reading a constraint name.
		versions.findById(sourceId).orElseThrow(() -> new PriceBookVersionNotFound(sourceId.toString()));
		if (versions.existsByVersionCode(versionCode)) {
			throw new DuplicateVersionCode(versionCode);
		}
		return versions.copy(sourceId, versionCode);
	}

	@Override
	public PriceBookSummary activate(UUID id) {
		PriceBookSummary version = versions.findById(id)
				.orElseThrow(() -> new PriceBookVersionNotFound(id.toString()));
		if (version.active()) {
			return version;                     // already the one; nothing to switch
		}
		versions.activate(id);
		return versions.findById(id).orElseThrow(() -> new PriceBookVersionNotFound(id.toString()));
	}
}
