package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionNotFound;
import com.burakpadr.decorating.quoting.domain.port.in.ManagePriceBookVersions;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
 * <p>There is deliberately no way to edit a version that has priced anything: every change is a new
 * version, reviewed while inactive, then activated. The bulk increase and the item correction are both
 * that shape rather than exceptions to it.
 *
 * <p>{@code GET /{id}} and {@code PUT /{id}/items/{code}} are not in §7's list; {@code docs/decisions/0015}
 * records why the panel cannot be built without them.
 *
 * <p>The failure statuses are documented because the panel acts on them: it tells the operator that a
 * code is taken or that a version is frozen, in those words. A contract that describes only the happy
 * path leaves the client casting its way to the message.
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

	@GetMapping("/{id}")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The version, its items and its coefficients",
					content = @Content(schema = @Schema(implementation = PriceBookDetailResponse.class))),
			@ApiResponse(responseCode = "404", description = "No version with that id",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
	PriceBookDetailResponse detail(@PathVariable UUID id) {
		return versions.detail(id).map(PriceBookDetailResponse::of)
				.orElseThrow(() -> new PriceBookVersionNotFound(id.toString()));
	}

	/**
	 * Corrects one line of a version nothing has been priced with. On a version that has priced
	 * something this is a conflict, not a mistake to explain away — the panel knows that from
	 * {@code editable} before the operator types anything.
	 */
	@PutMapping("/{id}/items/{code}")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The item as it now stands",
					content = @Content(schema = @Schema(implementation = PriceBookItemResponse.class))),
			@ApiResponse(responseCode = "400", description = "A figure the price list cannot hold",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "404", description = "No version with that id",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409",
					description = "The version has priced quotes; copy it and edit the copy",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
	PriceBookItemResponse updateItem(
			@PathVariable UUID id, @PathVariable ItemCode code, @Valid @RequestBody UpdateItemRequest request) {
		return PriceBookItemResponse.of(versions.updateItem(
				id, code, request.labourCost(), request.materialCost(), request.labourMinutes()));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "The new version, inactive",
					content = @Content(schema = @Schema(implementation = PriceBookSummaryResponse.class))),
			@ApiResponse(responseCode = "404", description = "No source version with that id",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "409", description = "That version code is taken",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
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
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "The raised version, inactive",
					content = @Content(schema = @Schema(implementation = PriceBookSummaryResponse.class))),
			@ApiResponse(responseCode = "400", description = "A percent nobody meant to type",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "404", description = "No version with that id",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
	PriceBookSummaryResponse bulkIncrease(
			@PathVariable UUID id, @Valid @RequestBody BulkIncreaseRequest request) {
		return PriceBookSummaryResponse.of(
				versions.applyBulkIncrease(id, request.target(), request.percent()));
	}

	@PostMapping("/{id}/activate")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The version, now live",
					content = @Content(schema = @Schema(implementation = PriceBookSummaryResponse.class))),
			@ApiResponse(responseCode = "404", description = "No version with that id",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
	PriceBookSummaryResponse activate(@PathVariable UUID id) {
		return PriceBookSummaryResponse.of(versions.activate(id));
	}
}
