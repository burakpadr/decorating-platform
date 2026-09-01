package com.burakpadr.decorating.customer.application;

import com.burakpadr.decorating.customer.domain.event.CustomerIdentified;
import com.burakpadr.decorating.customer.domain.model.Customer;
import com.burakpadr.decorating.customer.domain.port.in.IdentifyCustomer;
import com.burakpadr.decorating.customer.domain.port.out.CustomerRepository;
import com.burakpadr.decorating.quoting.domain.event.PhoneVerified;
import com.burakpadr.decorating.shared.PhoneNumber;
import com.burakpadr.decorating.shared.Uuid7;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The customer module's whole job in v1: turn a proved phone number into a row (§4.1, BOYA-45).
 *
 * <p>It learns that a number was proved from {@code quoting}'s {@code PhoneVerified} and answers with
 * its own {@code CustomerIdentified}. That round trip is the module seam doing its work rather than a
 * ceremony around a method call: {@code quoting} may not call in here and this module may not reach
 * into {@code quote_request}, so the customer id travels back the only way it can (decision 0019).
 *
 * <p>{@code REQUIRES_NEW} because this runs after the verification has committed. Without a
 * transaction of its own there would be nothing to commit, and nothing to commit means the
 * {@code AFTER_COMMIT} listener waiting on the far side never runs at all.
 */
@Service
class CustomerIdentificationService implements IdentifyCustomer {

	private final CustomerRepository customers;
	private final ApplicationEventPublisher events;
	private final Clock clock = Clock.systemUTC();

	CustomerIdentificationService(CustomerRepository customers, ApplicationEventPublisher events) {
		this.customers = customers;
		this.events = events;
	}

	@Override
	@Transactional
	public Customer identify(PhoneNumber phone) {
		return customers.findByPhone(phone).orElseGet(() ->
				customers.save(Customer.identifiedBy(Uuid7.generate(), phone, clock.instant())));
	}

	@TransactionalEventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void onPhoneVerified(PhoneVerified verified) {
		Customer customer = identify(verified.phone());
		events.publishEvent(
				new CustomerIdentified(customer.id(), verified.quoteRequestId(), clock.instant()));
	}
}
