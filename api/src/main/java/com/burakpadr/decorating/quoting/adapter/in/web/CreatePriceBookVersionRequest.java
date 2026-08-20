package com.burakpadr.decorating.quoting.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * "Copy this version under that code" (§7's {@code POST /api/op/price-books}).
 *
 * <p>{@code sourceId} is required rather than defaulting to the active version. A quarterly increase
 * is normally a copy of what is live, but making that implicit means a mis-click copies whatever
 * happened to be active — and the operator would have no way to see, afterwards, what the new list was
 * built from.
 */
record CreatePriceBookVersionRequest(
		@NotNull UUID sourceId,
		@NotBlank @Size(max = 32) @Pattern(
				regexp = "[A-Z0-9-]+",
				message = "a version code is upper case letters, digits and hyphens")
		String versionCode) {}
