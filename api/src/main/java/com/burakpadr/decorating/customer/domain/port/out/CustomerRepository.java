package com.burakpadr.decorating.customer.domain.port.out;

import com.burakpadr.decorating.customer.domain.model.Customer;
import com.burakpadr.decorating.shared.PhoneNumber;
import java.util.Optional;

/**
 * The {@code customer} rows (§4.1). Owned by this module and reachable from no other.
 */
public interface CustomerRepository {

	Optional<Customer> findByPhone(PhoneNumber phone);

	/**
	 * Inserts, or returns the row that got there first.
	 *
	 * <p>{@code customer.phone} is UNIQUE, and two verifications of the same number can be in flight at
	 * once — a customer with two tabs, or one who pressed twice. Losing that race is a normal outcome
	 * and not an error.
	 */
	Customer save(Customer customer);
}
