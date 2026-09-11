package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.SetupStatus;
import com.burakpadr.decorating.quoting.domain.port.in.ReadSetupStatus;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the active version and asks {@link SetupStatus} what is left (BOYA-69).
 *
 * <p>All of the judgement is in the domain record, which is the point: the same rule decides what
 * this endpoint reports and what the pricing paths refuse on, so an install cannot be quotable and
 * incomplete at the same time.
 */
@Service
@Transactional(readOnly = true)
class SetupStatusService implements ReadSetupStatus {

	private final PriceBookRepository priceBooks;

	SetupStatusService(PriceBookRepository priceBooks) {
		this.priceBooks = priceBooks;
	}

	@Override
	public SetupStatus status() {
		return SetupStatus.of(priceBooks.findActive());
	}
}
