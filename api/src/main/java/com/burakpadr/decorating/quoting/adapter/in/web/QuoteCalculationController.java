package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.QuoteCalculationCommand;
import com.burakpadr.decorating.quoting.domain.port.in.CalculateQuote;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The internal tool (workflow §12, increment 1): price a job by hand and see the breakdown.
 *
 * <p>This is what makes increment 1 shippable. The riskiest assumption in the system is whether the
 * engine's figures are figures this business would charge, and the cheapest way to find out is to type
 * in a job whose price is already known — no website, no photographs, no customer.
 *
 * <p>A POST that stores nothing. It is a question, not a quote request: no row is written, no event is
 * published, and asking twice costs nothing. POST rather than GET because the question is a dozen
 * fields, and a URL is the wrong place for them.
 *
 * <p>Not in §7's list; {@code docs/decisions/0015} records why the panel needs endpoints that section
 * does not have.
 */
@RestController
@RequestMapping("/api/op/price-calculations")
class QuoteCalculationController {

	private final CalculateQuote calculator;

	QuoteCalculationController(CalculateQuote calculator) {
		this.calculator = calculator;
	}

	@PostMapping
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "The price and everything assumed to reach it",
					content = @Content(schema = @Schema(implementation = QuoteCalculationResponse.class))),
			@ApiResponse(responseCode = "400", description = "A job the price book cannot price",
					content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
	QuoteCalculationResponse calculate(@Valid @RequestBody CalculateQuoteRequest request) {
		return QuoteCalculationResponse.of(calculator.calculate(new QuoteCalculationCommand(
				request.districtCode(),
				request.area(),
				request.areaBasis(),
				request.layout(),
				request.scope(),
				request.selectedRoomsOrEmpty(),
				request.wallCondition(),
				request.furnishing(),
				request.doorCount(),
				request.isDoorColourChange(),
				request.isDoorCountEstimated(),
				request.isWithElevator(),
				request.isRush())));
	}
}
