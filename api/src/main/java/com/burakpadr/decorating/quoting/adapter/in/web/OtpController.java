package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.config.session.CustomerSession;
import com.burakpadr.decorating.quoting.domain.port.in.SendOtp;
import com.burakpadr.decorating.quoting.domain.port.in.VerifyOtp;
import com.burakpadr.decorating.shared.PhoneNumber;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * §7's {@code /api/otp/*} (workflow §3.1, BOYA-45).
 *
 * <p>Neither route names a quote request, so both take a {@link CustomerSession}: the cookie says
 * which draft is being verified and the caller does not get to choose. That is not a convenience — a
 * body-supplied request id here would let anybody with a stranger's id have a code sent to their own
 * phone and verify somebody else's quote.
 */
@RestController
@RequestMapping("/api/otp")
@Tag(name = "Verification", description = "Phone verification (workflow §3.1)")
class OtpController {

	private final SendOtp send;
	private final VerifyOtp verify;

	OtpController(SendOtp send, VerifyOtp verify) {
		this.send = send;
		this.verify = verify;
	}

	@PostMapping("/send")
	@Operation(summary = "Send a verification code to a number")
	@ApiResponses({
			@ApiResponse(responseCode = "202",
					description = "Queued — see §13 on what 'sent' means here — with the code's expiry"),
			@ApiResponse(responseCode = "400", description = "Not a Turkish mobile number", content = {}),
			@ApiResponse(responseCode = "401", description = "No session cookie", content = {}),
			@ApiResponse(responseCode = "429", description = "§11's limits", content = {})})
	ResponseEntity<SendOtpResponse> sendCode(@Parameter(hidden = true) CustomerSession session,
			@Valid @RequestBody SendOtpRequest request, HttpServletRequest http) {
		// 202: the message is handed to a provider that does not exist yet (BOYA-6), so it is queued.
		// Answering 200 would say something arrived.
		return ResponseEntity.accepted().body(new SendOtpResponse(
				send.send(session.quoteRequestId(), PhoneNumber.of(request.phone()), addressKey(http))));
	}

	@PostMapping("/verify")
	@Operation(summary = "Verify the code, creating the customer")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Verified", content = {}),
			@ApiResponse(responseCode = "401", description = "No session cookie", content = {}),
			@ApiResponse(responseCode = "422", description = "Wrong, expired or already used",
					content = {}),
			@ApiResponse(responseCode = "423", description = "§11's fifth wrong guess", content = {})})
	ResponseEntity<Void> verifyCode(@Parameter(hidden = true) CustomerSession session,
			@Valid @RequestBody VerifyOtpRequest request) {
		verify.verify(session.quoteRequestId(), request.code());
		return ResponseEntity.noContent().build();
	}

	/**
	 * §11's address scope key.
	 *
	 * <p>Read from {@code X-Forwarded-For} where there is one, and this is not a nicety: the
	 * application sits behind Caddy, so {@code getRemoteAddr} is the proxy for every customer alive.
	 * Keyed on that, "ten an hour from an address" would be ten an hour from the whole internet — a
	 * limit meant to slow an attacker down would lock the business out of its own funnel instead.
	 *
	 * <p>The leftmost entry is the client as the first proxy saw it. It is client-supplied and can be
	 * forged, which is tolerable here precisely because §11 makes this the loose limit: the phone is
	 * the one that matters, and CGNAT already means an address is thousands of people.
	 */
	private static String addressKey(HttpServletRequest http) {
		String forwarded = http.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return "ip:" + forwarded.split(",")[0].trim();
		}
		return "ip:" + http.getRemoteAddr();
	}
}
