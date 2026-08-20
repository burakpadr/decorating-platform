package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.DuplicateVersionCode;
import com.burakpadr.decorating.quoting.domain.model.IncreaseTarget;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionNotFound;
import com.burakpadr.decorating.quoting.domain.port.in.ManagePriceBookVersions;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookVersionRepository;
import java.math.BigDecimal;
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

	private static final BigDecimal MIN_PERCENT = new BigDecimal("-50");
	private static final BigDecimal MAX_PERCENT = new BigDecimal("200");

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
	public PriceBookSummary applyBulkIncrease(UUID sourceId, IncreaseTarget target, BigDecimal percent) {
		if (percent.signum() == 0 || percent.compareTo(MIN_PERCENT) < 0
				|| percent.compareTo(MAX_PERCENT) > 0) {
			// Bounded because a mistyped percent prices every quote until somebody notices, and non-zero
			// because a version identical to its source is one nobody can tell apart afterwards.
			throw new IllegalArgumentException(
					"a bulk increase is between " + MIN_PERCENT + "% and " + MAX_PERCENT + "%, and never 0");
		}
		PriceBookSummary source = versions.findById(sourceId)
				.orElseThrow(() -> new PriceBookVersionNotFound(sourceId.toString()));

		PriceBookSummary copy = versions.copy(sourceId, nextFreeCode(source.versionCode()));
		versions.increaseItemCosts(copy.id(), target, percent);
		return copy;
	}

	/** REAL-2026-01 becomes REAL-2026-02, and on past whatever already exists. */
	private String nextFreeCode(String sourceCode) {
		String candidate = PriceBookVersionCode.next(sourceCode);
		while (versions.existsByVersionCode(candidate)) {
			candidate = PriceBookVersionCode.next(candidate);
		}
		return candidate;
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
