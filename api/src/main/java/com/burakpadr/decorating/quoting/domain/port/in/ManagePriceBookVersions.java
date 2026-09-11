package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.IncreaseTarget;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBookCoefficients;
import com.burakpadr.decorating.quoting.domain.model.PriceBookDetail;
import com.burakpadr.decorating.quoting.domain.model.PriceBookItem;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import com.burakpadr.decorating.quoting.domain.model.ServiceDistrict;
import com.burakpadr.decorating.quoting.domain.service.ActivationCheck;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The operator's price book management use case (§7, workflow §6).
 *
 * <p>Three operations, and the shape of them is the guarantee: a version is created by copying an
 * existing one, edited while it is inactive, and then activated. There is no operation that changes a
 * version's figures once it has priced anything, which is what lets a customer turn up with a
 * two-week-old quote and be told exactly how it was arrived at ({@code docs/decisions/0010}).
 */
public interface ManagePriceBookVersions {

	List<PriceBookSummary> list();

	/** One version with its figures, and whether it can still be edited. */
	Optional<PriceBookDetail> detail(UUID id);

	/**
	 * Builds a version from the shipped structure (BOYA-70): every item with its unit and duration,
	 * every room type's coefficients, every modifier — and <b>no money</b>. Inactive, like a copy.
	 *
	 * <p>This is how the first version comes into being. {@link #createVersionFrom} cannot make it:
	 * there is nothing to copy on an install whose migrations carry no price book (decision 0024).
	 *
	 * <p>Not restricted to being the first, though the wizard is its only expected caller. A rule
	 * saying "only when no version exists" would exist to prevent a version with no money in it, and
	 * that is what activation already refuses — one guard in the right place rather than two.
	 */
	PriceBookSummary createFromStructure(String versionCode);

	/**
	 * Sets a version's service districts, replacing whatever it had. The whole list rather than one
	 * district at a time: this is the answer to "where does this business work", and a partial update
	 * makes it possible to open an area while leaving another one open by accident.
	 *
	 * <p>Closing a district is therefore a new version, like every other price book change — §4.5's
	 * promise covers the factor a quote was priced with as much as it covers the item costs.
	 *
	 * @throws com.burakpadr.decorating.quoting.domain.model.PriceBookVersionLocked if the version is
	 *     live or any quote points at it
	 */
	PriceBookDetail replaceDistricts(UUID versionId, List<ServiceDistrict> districts);

	/**
	 * Copies a version whole — items, modifiers, room types and districts — under a new code. The copy
	 * starts inactive: a list nobody has looked at must not be pricing quotes.
	 */
	PriceBookSummary createVersionFrom(UUID sourceId, String versionCode);

	/**
	 * Makes a version the one quotes are priced against, switching off whichever was.
	 *
	 * <p>The version is checked first, because this is the moment it stops being a draft somebody is
	 * editing and starts answering customers ({@link ActivationCheck}).
	 *
	 * @throws com.burakpadr.decorating.quoting.domain.model.PriceBookNotActivatable if an item code is
	 *     missing, a room type has no coefficients, or an item's labour contradicts the version's own
	 *     crew rate (ADR 0016)
	 */
	PriceBookSummary activate(UUID id);

	/**
	 * Sets the version's coefficients — the figures that are not item costs — on a version nothing has
	 * been priced with. Item labour costs are <b>re-derived</b> from the new crew rate rather than left
	 * where they were (ADR 0016), which is why crew size and crew day cost arrive together.
	 *
	 * <p>There is no "edit the live version": §4.5's promise is that a figure a customer was shown
	 * stays explainable, so the shape here is the one items already use — copy, edit the copy, activate.
	 *
	 * @throws com.burakpadr.decorating.quoting.domain.model.PriceBookVersionLocked if the version is
	 *     live or any quote points at it
	 */
	PriceBookDetail updateCoefficients(UUID versionId, PriceBookCoefficients coefficients);

	/**
	 * The quarterly increase (workflow §6): copies a version, raises one or both halves of every item
	 * cost by a percentage, and hands back the copy. The source is not touched — that is the whole shape
	 * of it. Durations are not touched either: a price rise does not make the work slower.
	 */
	PriceBookSummary applyBulkIncrease(UUID sourceId, IncreaseTarget target, BigDecimal percent);

	/**
	 * Corrects one item on a version nothing has been priced with — the "edit" half of §7's
	 * "clone + edit". Unlike a bulk increase this also sets the duration, which is the point: what an
	 * operator knows about an item is how long it takes and what it costs in paint.
	 *
	 * <p>There is no labour cost to pass. It is derived from {@code labourMinutes} at the version's crew
	 * rate (ADR 0016) and returned on the item, so the caller can show what the change did. Making
	 * labour more expensive is a change to {@code crew_day_cost}, not to fourteen items.
	 *
	 * @throws com.burakpadr.decorating.quoting.domain.model.PriceBookVersionLocked if the version is
	 *     live or any quote points at it
	 */
	PriceBookItem updateItem(UUID versionId, ItemCode code, BigDecimal materialCost,
			BigDecimal labourMinutes);
}
