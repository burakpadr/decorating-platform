package com.burakpadr.decorating.customer.adapter.out.persistence;

import com.burakpadr.decorating.customer.domain.model.Customer;
import com.burakpadr.decorating.customer.domain.model.CustomerType;
import com.burakpadr.decorating.customer.domain.port.out.CustomerRepository;
import com.burakpadr.decorating.shared.PhoneNumber;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

/**
 * {@code customer} rows (§4.1).
 *
 * <p>{@code ON CONFLICT (phone) DO NOTHING} and then a read: the insert is the lock. Two verifications
 * of the same number can be in flight at once — one customer with two tabs is enough — and
 * {@code customer.phone} is UNIQUE, so the loser of that race must find the winner's row rather than
 * fail. Doing it as a read-then-insert would leave exactly the window this closes.
 */
@Component
class CustomerPersistenceAdapter implements CustomerRepository {

	private static final RowMapper<Customer> AS_CUSTOMER = (row, index) -> new Customer(
			row.getObject("id", UUID.class),
			PhoneNumber.of(row.getString("phone")),
			CustomerType.valueOf(row.getString("customer_type")),
			row.getTimestamp("created_at").toInstant());

	private final JdbcTemplate jdbc;

	CustomerPersistenceAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<Customer> findByPhone(PhoneNumber phone) {
		return first(jdbc.query("SELECT * FROM customer WHERE phone = ? AND deleted_at IS NULL",
				AS_CUSTOMER, phone.e164()));
	}

	@Override
	public Customer save(Customer customer) {
		jdbc.update("""
				INSERT INTO customer (id, phone, customer_type, created_at)
				VALUES (?, ?, ?, ?)
				ON CONFLICT (phone) DO NOTHING
				""",
				customer.id(), customer.phone().e164(), customer.type().name(),
				Timestamp.from(customer.createdAt()));

		// Re-read rather than trust the insert: on conflict the row that exists is somebody else's, and
		// it is the one every later reference has to agree on.
		return findByPhone(customer.phone()).orElseThrow(() -> new IllegalStateException(
				"a customer row was neither written nor found for a number that was just verified"));
	}

	private static Optional<Customer> first(List<Customer> rows) {
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
	}
}
