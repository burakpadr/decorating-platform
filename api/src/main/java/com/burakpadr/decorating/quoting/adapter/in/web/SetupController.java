package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.port.in.ReadSetupStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the panel asks before it renders anything (BOYA-69, {@code docs/decisions/0025}).
 *
 * <p>Inside the operator realm, like everything else under {@code /api/op}. What an install has not
 * configured is not a visitor's business, and the answer to a visitor is the refusal the pricing
 * endpoints already give.
 */
@RestController
class SetupController {

	private final ReadSetupStatus setup;

	SetupController(ReadSetupStatus setup) {
		this.setup = setup;
	}

	@GetMapping("/api/op/setup/status")
	@Operation(summary = "Whether this install has been set up, and what is still missing")
	@ApiResponses(@ApiResponse(responseCode = "200", description = "The settings setup still owes"))
	SetupStatusResponse status() {
		return SetupStatusResponse.of(setup.status());
	}
}
