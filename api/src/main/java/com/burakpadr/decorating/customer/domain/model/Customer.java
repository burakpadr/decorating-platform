package com.burakpadr.decorating.customer.domain.model;

import com.burakpadr.decorating.shared.PhoneNumber;
import java.time.Instant;
import java.util.UUID;

/**
 * A person the business can reach (§4.1).
 *
 * <p>A row exists only once a phone number has been proved — the table's own comment says so, and
 * {@code quote_request.pending_phone} exists precisely so that an unproved number has somewhere else
 * to live. Everything before verification is a stranger with a draft.
 *
 * <p>Identity is the number. §4.1: "lookup is by phone: a returning customer resolves to the existing
 * row, which is how repeat business becomes visible" — and it is why {@code customer.phone} is UNIQUE
 * rather than merely indexed.
 */
public record Customer(UUID id, PhoneNumber phone, CustomerType type, Instant createdAt) {

	public Customer {
		if (id == null || phone == null) {
			throw new IllegalArgumentException("a customer is an identity and a number we can reach");
		}
		if (type == null) {
			type = CustomerType.INDIVIDUAL;
		}
	}

	/** What §3 knows at verification: a number, and nothing else yet. */
	public static Customer identifiedBy(UUID id, PhoneNumber phone, Instant when) {
		return new Customer(id, phone, CustomerType.INDIVIDUAL, when);
	}
}
