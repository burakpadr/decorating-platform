package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.event.QuoteRequestSubmitted;
import com.burakpadr.decorating.quoting.domain.model.CaptureIncomplete;
import com.burakpadr.decorating.quoting.domain.model.CaptureState;
import com.burakpadr.decorating.quoting.domain.model.PhoneNotVerified;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.port.in.ReadCaptureState;
import com.burakpadr.decorating.quoting.domain.port.in.SubmitQuoteRequest;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.VerifiedPhoneWriter;
import com.burakpadr.decorating.quoting.domain.service.BusinessHours;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The end of what the customer does (workflow §3.2, BOYA-45).
 *
 * <p>§3's arrow into ANALYZING carries two conditions and this is where they are finally checked. They
 * were written on the diagram and in {@code QuoteRequest.submit()}'s javadoc from the start, and
 * enforced nowhere: the aggregate holds neither a photograph count nor a verification, so asking it to
 * guard them would have meant giving it both.
 *
 * <p>The answer is a promise. §3.2: the customer is told when the quote will be ready and that they
 * may leave the screen — and the time is computed against working hours, because "2 saat içinde" said
 * at 23:00 is a sentence the customer only discovers is false by waiting for it.
 */
@Service
class QuoteRequestSubmissionService implements SubmitQuoteRequest {

	private final QuoteRequestRepository requests;
	private final ReadCaptureState capture;
	private final VerifiedPhoneWriter phones;
	private final ApplicationEventPublisher events;
	private final BusinessHours hours;
	private final Clock clock = Clock.systemUTC();

	QuoteRequestSubmissionService(QuoteRequestRepository requests, ReadCaptureState capture,
			VerifiedPhoneWriter phones, ApplicationEventPublisher events,
			@Value("${decorating.business-hours.zone:Europe/Istanbul}") String zone,
			@Value("${decorating.business-hours.open:09:00}") String opens,
			@Value("${decorating.business-hours.close:18:00}") String closes,
			@Value("${decorating.business-hours.sla:PT2H}") Duration sla) {
		this.requests = requests;
		this.capture = capture;
		this.phones = phones;
		this.events = events;
		this.hours = new BusinessHours(
				ZoneId.of(zone), LocalTime.parse(opens), LocalTime.parse(closes), sla);
	}

	@Override
	@Transactional
	public Submission submit(UUID quoteRequestId) {
		QuoteRequest request = requests.findById(quoteRequestId)
				.orElseThrow(() -> new QuoteRequestNotFound(String.valueOf(quoteRequestId)));

		// Photographs first: it is the condition the customer is most likely to be able to fix from
		// where they are standing, and telling them about the other one first sends them to a screen
		// that would refuse them again.
		CaptureState state = capture.of(quoteRequestId);
		if (!state.complete()) {
			throw new CaptureIncomplete(state.required(), state.taken());
		}
		if (phones.verifiedAt(quoteRequestId).isEmpty()) {
			throw new PhoneNotVerified("this request has no verified phone number yet");
		}

		Instant now = clock.instant();
		requests.save(request.submit());
		events.publishEvent(new QuoteRequestSubmitted(quoteRequestId, now));

		return new Submission(quoteRequestId, hours.promiseFrom(now));
	}
}
