package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import java.time.Instant;
import java.util.UUID;

/**
 * A price book version as the operator panel sees it (§7).
 *
 * <p>An operator-realm response, and still no figures: the list answers "which versions exist", and
 * nothing about it needs a cost. The customer-facing types are separate classes elsewhere — §1's rule
 * is one of separate types, never a shared type with fields stripped conditionally.
 */
record PriceBookSummaryResponse(UUID id, String versionCode, boolean active, Instant createdAt) {

	static PriceBookSummaryResponse of(PriceBookSummary summary) {
		return new PriceBookSummaryResponse(
				summary.id(), summary.versionCode(), summary.active(), summary.createdAt());
	}
}
