package com.burakpadr.decorating.quoting.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import java.time.Instant;
import java.util.UUID;

/**
 * A price book version as the operator panel sees it (§7).
 *
 * <p>An operator-realm response, and still no figures: the list answers "which versions exist", and
 * nothing about it needs a cost. The customer-facing types are separate classes elsewhere — §1's rule
 * is one of separate types, never a shared type with fields stripped conditionally.
 *
 * <p>Every field is always present, and the contract says so. The generated client's types come
 * straight from it, so an optional field here becomes a null check in every component that reads a
 * version — and eventually a component that forgets one.
 */
record PriceBookSummaryResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String versionCode,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {

	static PriceBookSummaryResponse of(PriceBookSummary summary) {
		return new PriceBookSummaryResponse(
				summary.id(), summary.versionCode(), summary.active(), summary.createdAt());
	}
}
