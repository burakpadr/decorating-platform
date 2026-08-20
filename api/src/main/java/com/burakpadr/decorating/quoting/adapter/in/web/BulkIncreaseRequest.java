package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.IncreaseTarget;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The quarterly increase (§7, workflow §6): "all labour items, 15%".
 *
 * <p>Bounded at the edge as well as in the use case. The panel should refuse a mistyped 1500% while
 * the operator is still looking at the form, rather than after it has produced a version somebody
 * later has to explain.
 */
record BulkIncreaseRequest(
		@NotNull IncreaseTarget target,
		@NotNull @DecimalMin("-50") @DecimalMax("200") BigDecimal percent) {}
