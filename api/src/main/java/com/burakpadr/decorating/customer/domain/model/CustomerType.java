package com.burakpadr.decorating.customer.domain.model;

/**
 * Mirrors {@code customer_type_check} (§4.1).
 *
 * <p>Only {@link #INDIVIDUAL} is reachable today: v1 asks for a phone number and nothing that would
 * tell the two apart. {@link #BUSINESS} is in the schema because the column is, and a Java enum that
 * knew fewer values than the constraint would turn a database refusal into a surprise.
 */
public enum CustomerType {
	INDIVIDUAL,
	BUSINESS
}
