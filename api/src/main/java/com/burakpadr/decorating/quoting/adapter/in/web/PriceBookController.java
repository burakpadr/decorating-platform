package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.port.in.ManagePriceBookVersions;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Price book versions, for the operator (§7, workflow §6).
 *
 * <p>Under {@code /api/op/**}, which is its own security realm — the panel's figures are internal and
 * a version list is the shape of the business's pricing.
 *
 * <p>Four endpoints and no fifth. There is deliberately no way to edit a version that has priced
 * anything: every change is a new version, reviewed while inactive, then activated. The bulk increase
 * is the same shape rather than an exception to it.
 */
@RestController
@RequestMapping("/api/op/price-books")
class PriceBookController {

	private final ManagePriceBookVersions versions;

	PriceBookController(ManagePriceBookVersions versions) {
		this.versions = versions;
	}

	@GetMapping
	List<PriceBookSummaryResponse> list() {
		return versions.list().stream().map(PriceBookSummaryResponse::of).toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PriceBookSummaryResponse create(@Valid @RequestBody CreatePriceBookVersionRequest request) {
		return PriceBookSummaryResponse.of(
				versions.createVersionFrom(request.sourceId(), request.versionCode()));
	}

	/**
	 * A percentage on every item cost, as a new version. The live list is not touched: the operator
	 * reviews the produced version and activates it, which is the same two steps as any other change.
	 */
	@PostMapping("/{id}/bulk-increase")
	@ResponseStatus(HttpStatus.CREATED)
	PriceBookSummaryResponse bulkIncrease(
			@PathVariable UUID id, @Valid @RequestBody BulkIncreaseRequest request) {
		return PriceBookSummaryResponse.of(
				versions.applyBulkIncrease(id, request.target(), request.percent()));
	}

	@PostMapping("/{id}/activate")
	PriceBookSummaryResponse activate(@PathVariable UUID id) {
		return PriceBookSummaryResponse.of(versions.activate(id));
	}
}
