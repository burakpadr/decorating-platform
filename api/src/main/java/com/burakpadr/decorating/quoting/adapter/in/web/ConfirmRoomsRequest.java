package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * The areas the customer settled on (workflow §2.2).
 *
 * <p>A list, not a set: two bathrooms are {@code BATHROOM} twice, and the order is the order they will
 * be photographed in. Labels are not sent — a label is customer-facing Turkish copy derived from the
 * type and its position (§4.3), and a client that could set it would own the wording on the capture
 * screen and in the operator's quote.
 */
record ConfirmRoomsRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
				example = "[\"LIVING_ROOM\",\"BEDROOM\",\"BEDROOM\",\"KITCHEN\",\"BATHROOM\"]")
		@NotEmpty List<RoomType> areas) {}
