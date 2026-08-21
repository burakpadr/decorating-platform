package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.ServiceDistrict;
import com.burakpadr.decorating.quoting.domain.port.in.ListServiceDistricts;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The served districts, from the version that is live (BOYA-26). */
@Service
@Transactional(readOnly = true)
class ServiceDistrictService implements ListServiceDistricts {

	private final PriceBookRepository priceBooks;

	ServiceDistrictService(PriceBookRepository priceBooks) {
		this.priceBooks = priceBooks;
	}

	@Override
	public List<ServiceDistrict> served() {
		return priceBooks.findActive()
				.orElseThrow(() -> new IllegalStateException(
						"no active price book: the served districts belong to a version"))
				.servedDistricts();
	}
}
