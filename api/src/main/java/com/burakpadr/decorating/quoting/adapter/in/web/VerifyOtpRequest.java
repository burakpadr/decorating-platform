package com.burakpadr.decorating.quoting.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** The six digits from the message (workflow §3.1). */
record VerifyOtpRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
		@NotBlank String code) {}
