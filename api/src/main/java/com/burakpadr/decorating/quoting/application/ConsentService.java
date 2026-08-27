package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.Consent;
import com.burakpadr.decorating.quoting.domain.model.ConsentNotice;
import com.burakpadr.decorating.quoting.domain.model.ConsentNoticeChanged;
import com.burakpadr.decorating.quoting.domain.model.ConsentOutOfOrder;
import com.burakpadr.decorating.quoting.domain.model.ConsentType;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.port.in.ReadConsentNotice;
import com.burakpadr.decorating.quoting.domain.port.in.RecordConsent;
import com.burakpadr.decorating.quoting.domain.port.out.ConsentNotices;
import com.burakpadr.decorating.quoting.domain.port.out.ConsentRepository;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Showing the data notice and recording what the customer decided (workflow §2.3, BOYA-39).
 *
 * <p>§2.3 puts the three shooting rules and this on one screen, and only this half leaves a trace:
 * "fotoğrafların ne için kullanılacağı ve ne kadar süre saklanacağı bilgisi burada verilir ve onayı
 * alınır". The trace has to survive the question asked months later — which words did this person
 * agree to — so the version is taken from the notice the server served, and a client that echoes a
 * version the server no longer publishes is sent back to read the new one.
 *
 * <p>No status moves. §3 draws one arrow into {@code PHOTOS_PENDING} and it belongs to §2.2; consent
 * is a fact recorded against a request that is already there, in the same way {@code acceptsPhotographs}
 * is a question rather than a transition.
 */
@Service
class ConsentService implements RecordConsent, ReadConsentNotice {

	private final ConsentRepository consents;
	private final ConsentNotices notices;
	private final QuoteRequestRepository requests;

	/** UTC, as the rest of the module does it: the column is {@code timestamptz}. */
	private final Clock clock = Clock.systemUTC();

	ConsentService(ConsentRepository consents, ConsentNotices notices,
			QuoteRequestRepository requests) {
		this.consents = consents;
		this.notices = notices;
		this.requests = requests;
	}

	@Override
	public ConsentNotice current(ConsentType type) {
		return notices.current(type);
	}

	@Override
	@Transactional
	public Consent record(UUID quoteRequestId, ConsentType type, boolean granted, String textVersion) {
		ConsentNotice notice = notices.current(type);
		if (!notice.version().equals(textVersion)) {
			throw new ConsentNoticeChanged(textVersion, notice.version());
		}

		QuoteRequest request = requests.findById(quoteRequestId)
				.orElseThrow(() -> new QuoteRequestNotFound(String.valueOf(quoteRequestId)));
		// §2.3 follows §2.2. Before the list is agreed the notice describes photographs of nothing, and
		// the row it would leave behind is indistinguishable from one somebody meant.
		if (!request.acceptsPhotographs()) {
			throw new ConsentOutOfOrder(
					"there is nothing to consent to yet: the areas to photograph are agreed first");
		}

		Consent decision = new Consent(Uuid7.generate(), quoteRequestId, type, granted,
				notice.version(), clock.instant());
		consents.save(decision);
		return decision;
	}

	@Override
	public Optional<Consent> latest(UUID quoteRequestId, ConsentType type) {
		return consents.latest(quoteRequestId, type);
	}
}
