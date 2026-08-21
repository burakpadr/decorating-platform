package com.burakpadr.decorating.quoting.domain.model;

import java.math.BigDecimal;
import java.util.Map;

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
 *
 * <p>{@code requiredPhotosByType} is here for the screen after it. Workflow §2.2 lets the customer add
 * an area the layout never implied — a second bathroom, a study, a balcony — and still promises a total
 * number of photographs up front. The frames per kind of area belong to the price book version (§5.3),
 * so the version that priced this range answers them; a copy living in the client would be free to
 * drift from the version behind the figure the customer agreed to.
 */
public record StageOneEstimate(
		BigDecimal low,
		BigDecimal high,
		BigDecimal bandRatio,
		BigDecimal netArea,
		boolean areaWasGross,
		RoomList rooms,
		Map<RoomType, Integer> requiredPhotosByType,
		String priceBookVersion) {

	public StageOneEstimate {
		requiredPhotosByType = Map.copyOf(requiredPhotosByType);
	}
}
