package com.burakpadr.decorating.customer.domain.port.in;

import com.burakpadr.decorating.customer.domain.model.Customer;
import com.burakpadr.decorating.shared.PhoneNumber;

/**
 * Find the customer this number belongs to, or make one (§4.1, BOYA-45).
 *
 * <p>Called in answer to {@code quoting}'s {@code PhoneVerified} and never directly from another
 * module — the seam is events (decision 0019). Idempotent by the number: verification of a number
 * that has bought paint before resolves to the row it already has.
 */
public interface IdentifyCustomer {

	Customer identify(PhoneNumber phone);
}
