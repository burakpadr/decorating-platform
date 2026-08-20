package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.IncreaseTarget;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Writing and listing price book versions.
 *
 * <p>Separate from {@link PriceBookRepository} on purpose. That port is what the pricing path holds,
 * and it can only read: an engine that could write its own price book would be one refactoring away
 * from doing it. Everything that changes the set of versions lives here, where only the management
 * use case reaches it.
 */
public interface PriceBookVersionRepository {

	/** Newest first — the operator is nearly always looking for what just happened. */
	List<PriceBookSummary> findAll();

	Optional<PriceBookSummary> findById(UUID id);

	boolean existsByVersionCode(String versionCode);

	/** Copies the source version and all four of its child tables under a new, inactive code. */
	PriceBookSummary copy(UUID sourceId, String versionCode);

	/** Switches the active version, leaving exactly one. */
	void activate(UUID id);

	/**
	 * Raises the item costs of one version in place, rounding to the cent.
	 *
	 * <p>In place is safe here and nowhere else: the only caller applies it to a copy it has just made,
	 * which no quote can point at yet.
	 */
	void increaseItemCosts(UUID priceBookId, IncreaseTarget target, BigDecimal percent);

	/**
	 * Whether anything has been priced with this version: not live, and no quote pointing at it.
	 *
	 * <p>Asked of the database rather than tracked as a flag, because the answer is a fact about the
	 * quote table and a flag would be a second copy of it that could disagree.
	 */
	boolean isEditable(UUID id);

	/** Sets one item's three figures on a version the caller has established is editable. */
	void updateItem(UUID priceBookId, ItemCode code, BigDecimal labourCost, BigDecimal materialCost,
			BigDecimal labourMinutes);
}
