package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;

/**
 * The range stage 1 shows a customer, and only that (§1, §2.4).
 *
 * <p>What is deliberately absent is the cost and the margin. The operator's screen carries both, on
 * purpose — comparing them is the point of that tool — and this is the other side of the same rule: a
 * customer-facing figure is a price, and a response that also contained what the job costs the business
 * would be one screenshot away from a conversation nobody wants to have.
 *
 * <p>{@code bandRatio} is here because §1.5 of the workflow requires the screen to say *why* the range
 * is wide ("duvar durumunu bilmediğimiz için"). A ratio the client cannot see is a reason the client
 * cannot give.
 */
public record StageOneEstimate(
		BigDecimal low,
		BigDecimal high,
		BigDecimal bandRatio,
		BigDecimal netArea,
		boolean areaWasGross,
		RoomList rooms,
		String priceBookVersion) {}
