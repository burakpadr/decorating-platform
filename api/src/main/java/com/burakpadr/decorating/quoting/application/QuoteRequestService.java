package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.port.in.ManageQuoteRequests;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating and answering a draft (BOYA-25). */
@Service
class QuoteRequestService implements ManageQuoteRequests {

	private final QuoteRequestRepository requests;

	QuoteRequestService(QuoteRequestRepository requests) {
		this.requests = requests;
	}

	@Override
	@Transactional
	public QuoteRequest createDraft() {
		// UUIDv7 (§4): time-ordered, so drafts cluster on disk in the order they were started, and a
		// sequential id would have leaked how many quotes the business gets.
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate());
		requests.save(draft);
		return draft;
	}

	@Override
	@Transactional
	public QuoteRequest answer(UUID id, StageOneAnswers patch) {
		// Read, merge in the domain, write the whole row. The alternative — an UPDATE with the patch's
		// non-null columns — would put the merge rule in SQL as well as in StageOneAnswers, and two
		// copies of one rule is the defect this codebase keeps finding (ADR 0016).
		QuoteRequest answered = requests.findById(id)
				.orElseThrow(() -> new QuoteRequestNotFound(id.toString()))
				.answer(patch);
		requests.save(answered);
		return answered;
	}
}
