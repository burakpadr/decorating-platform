package com.burakpadr.decorating.quoting.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * The number to send the range to (§1.5).
 *
 * <p>Validated as a Turkish mobile by {@code PhoneNumber}, not by an annotation: the same rule decides
 * what {@code customer.phone} looks like, and two spellings of one rule is how one person ends up as two
 * customers.
 */
record SendEstimateSmsRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "0555 123 45 67")
		@NotBlank String phone) {}
