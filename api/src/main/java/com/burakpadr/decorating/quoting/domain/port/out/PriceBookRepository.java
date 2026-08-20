package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import java.util.Optional;

/**
 * Reading price book versions (§2 outbound ports, §4.5).
 *
 * <p>An interface in the domain because the engine's whole testability rests on the price book being
 * a value it is handed, not something it fetches. The adapter that maps rows onto {@link PriceBook}
 * lives in {@code adapter/out/persistence} and is the only place that knows the table exists.
 *
 * <p>There is no {@code save} here. New versions are created by the price book management use case,
 * which supersedes rather than edits — a version that quotes already point at is immutable
 * ({@code docs/decisions/0010}), so a general-purpose save would be the wrong shape for the one
 * operation that is allowed.
 */
public interface PriceBookRepository {

	/** The single version quotes are priced against, found by {@code active = true}. */
	Optional<PriceBook> findActive();

	/** A specific version, so a quote already sent stays readable against the figures that made it. */
	Optional<PriceBook> findByVersionCode(String versionCode);
}
