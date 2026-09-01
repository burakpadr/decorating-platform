package com.burakpadr.decorating.quoting.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * When the code stops working (workflow §3.1).
 *
 * <p>Sent so the screen can count it down. The lifetime is configuration and the client has no way to
 * know it — a hard-coded five minutes here would be a clock that drifts from the server's the day the
 * value changes, and the customer would find out by typing a code the countdown said was still good.
 *
 * <p>An instant, not a duration: the two clocks are already imperfectly aligned and a duration would
 * start counting from whenever the response happened to be rendered.
 */
record SendOtpResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt) {}
