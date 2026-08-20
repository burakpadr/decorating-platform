package com.burakpadr.decorating.quoting.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A price book version without its figures (§7's {@code GET /api/op/price-books}).
 *
 * <p>Listing versions is a different question from pricing with one, and loading 65 rows per version
 * to answer it would be work nobody asked for. This is what the operator's list needs and nothing
 * more.
 */
public record PriceBookSummary(UUID id, String versionCode, boolean active, Instant createdAt) {}
