package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.ActivationProblem;
import com.burakpadr.decorating.quoting.domain.model.DuplicateVersionCode;
import com.burakpadr.decorating.quoting.domain.model.IncreaseTarget;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookCoefficients;
import com.burakpadr.decorating.quoting.domain.model.PriceBookDetail;
import com.burakpadr.decorating.quoting.domain.model.PriceBookItem;
import com.burakpadr.decorating.quoting.domain.model.PriceBookNotActivatable;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionLocked;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionNotFound;
import com.burakpadr.decorating.quoting.domain.port.in.ManagePriceBookVersions;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookVersionRepository;
import com.burakpadr.decorating.quoting.domain.service.ActivationCheck;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
	private final PriceBookRepository books;
	private final ActivationCheck activationCheck = new ActivationCheck();

	PriceBookVersionService(PriceBookVersionRepository versions, PriceBookRepository books) {
		this.versions = versions;
		this.books = books;
	}

	@Override
	@Transactional(readOnly = true)
	public List<PriceBookSummary> list() {
		return versions.findAll();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<PriceBookDetail> detail(UUID id) {
		return versions.findById(id).flatMap(summary -> books.findById(id)
				.map(book -> new PriceBookDetail(summary, book, versions.isEditable(id))));
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
	public PriceBookItem updateItem(UUID versionId, ItemCode code, BigDecimal materialCost,
			BigDecimal labourMinutes) {
		PriceBookSummary version = versions.findById(versionId)
				.orElseThrow(() -> new PriceBookVersionNotFound(versionId.toString()));
		if (!versions.isEditable(versionId)) {
			throw new PriceBookVersionLocked(version.versionCode());
		}
		if (materialCost.signum() < 0 || labourMinutes.signum() <= 0) {
			// Zero minutes would drop the item out of the duration and the minimum without changing a
			// single price, which is the kind of wrong nobody sees (§5.8). Since ADR 0016 it would also
			// zero the item's labour cost, so the same line item would be free to do.
			throw new IllegalArgumentException(
					"costs cannot be negative and an item cannot take no time");
		}
		versions.updateItem(versionId, code, materialCost, labourMinutes);
		// Read back rather than assembled here: the unit belongs to the row, and a response built from
		// the request would happily report a unit the database disagrees with.
		return books.findById(versionId).orElseThrow(() -> new PriceBookVersionNotFound(versionId.toString()))
				.item(code);
	}

	@Override
	public PriceBookDetail updateCoefficients(UUID versionId, PriceBookCoefficients coefficients) {
		PriceBookSummary version = versions.findById(versionId)
				.orElseThrow(() -> new PriceBookVersionNotFound(versionId.toString()));
		if (!versions.isEditable(versionId)) {
			throw new PriceBookVersionLocked(version.versionCode());
		}
		// The bounds are the record's own (§5.3–5.5's coefficients multiply every square metre), so a
		// value that cannot be right never reaches the database.
		versions.updateCoefficients(versionId, coefficients);
		// Read back rather than assembled from the request: the caller's next question is what the
		// change did to the fourteen items, and only the database can answer that.
		return detail(versionId).orElseThrow(() -> new PriceBookVersionNotFound(versionId.toString()));
	}

	@Override
	public PriceBookSummary activate(UUID id) {
		PriceBookSummary version = versions.findById(id)
				.orElseThrow(() -> new PriceBookVersionNotFound(id.toString()));
		if (version.active()) {
			return version;                     // already the one; nothing to switch
		}

		// The gate. Everything it refuses is a defect the engine would otherwise meet against a real
		// customer — a code it looks up and cannot find, or a labour figure that contradicts this same
		// book's crew rate (ADR 0016). It sits here rather than on every write because an inactive
		// version is allowed to be half-finished: the panel and the wizard both build one field at a
		// time. Until BOYA-72 this was a schema test reading the active book out of the migrations;
		// there is no such book any more, and a version can now arrive from setup as well.
		PriceBook book = books.findById(id)
				.orElseThrow(() -> new PriceBookVersionNotFound(id.toString()));
		List<ActivationProblem> problems = activationCheck.check(book);
		if (!problems.isEmpty()) {
			throw new PriceBookNotActivatable(version.versionCode(), problems);
		}

		versions.activate(id);
		return versions.findById(id).orElseThrow(() -> new PriceBookVersionNotFound(id.toString()));
	}
}
