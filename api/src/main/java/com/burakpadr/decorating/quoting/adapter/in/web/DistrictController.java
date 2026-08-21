package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.port.in.ListServiceDistricts;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The districts we work in (§7, BOYA-26).
 *
 * <p>No session: this is the question asked before a draft exists, and needing a cookie to find out
 * whether we serve an area would mean creating a request for every visitor who turns out to live
 * somewhere else.
 */
@RestController
class DistrictController {

	private final ListServiceDistricts districts;

	DistrictController(ListServiceDistricts districts) {
		this.districts = districts;
	}

	@GetMapping("/api/districts")
	@Operation(summary = "The districts stage 1 offers, from the live price book")
	@ApiResponses(@ApiResponse(responseCode = "200", description = "Served districts, by name"))
	List<DistrictResponse> served() {
		return districts.served().stream().map(DistrictResponse::of).toList();
	}
}
