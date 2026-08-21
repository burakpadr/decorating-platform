package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.model.TemplateCode;
import com.burakpadr.decorating.quoting.domain.port.in.SendEstimateBySms;
import com.burakpadr.decorating.quoting.domain.port.out.NotificationLog;
import com.burakpadr.decorating.quoting.domain.port.out.PendingPhoneWriter;
import com.burakpadr.decorating.quoting.domain.port.out.ResumeTokens;
import com.burakpadr.decorating.quoting.domain.port.out.SmsSender;
import com.burakpadr.decorating.quoting.domain.port.out.StoredEstimates;
import com.burakpadr.decorating.quoting.adapter.out.notification.SmsTemplates;
import com.burakpadr.decorating.shared.PhoneNumber;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * §1.5's third option: the range, to a phone (BOYA-33).
 *
 * <p>The order matters. The number is stored and the row is written whatever the provider does, because
 * the point of this feature is the number: a customer who sees a range and leaves is unreachable
 * otherwise, and §1.5 says that is the largest loss in the funnel. Sending is the part that can fail.
 */
@Service
class EstimateSmsService implements SendEstimateBySms {

	private final StoredEstimates estimates;
	private final PendingPhoneWriter phones;
	private final ResumeTokens resumeTokens;
	private final NotificationLog notifications;
	private final SmsSender sender;
	private final SmsTemplates templates;
	private final String webBaseUrl;

	EstimateSmsService(StoredEstimates estimates, PendingPhoneWriter phones, ResumeTokens resumeTokens,
			NotificationLog notifications, SmsSender sender, SmsTemplates templates,
			@Value("${decorating.web.base-url:https://localhost:3000}") String webBaseUrl) {
		this.estimates = estimates;
		this.phones = phones;
		this.resumeTokens = resumeTokens;
		this.notifications = notifications;
		this.sender = sender;
		this.templates = templates;
		this.webBaseUrl = webBaseUrl;
	}

	@Override
	@Transactional
	public void send(UUID quoteRequestId, PhoneNumber to) {
		StoredEstimates.Range range = estimates.find(quoteRequestId)
				.orElseThrow(() -> new QuoteRequestNotFound(quoteRequestId.toString()));
		if (range.low() == null || range.high() == null) {
			// The message *is* the range (§13's template), so there is nothing to send yet. Refused rather
			// than sent empty: an SMS reading "Boya tahmininiz: ." costs the same as a useful one.
			throw new IllegalStateException(
					"no range has been computed for " + quoteRequestId + " yet");
		}

		// Kept first. Everything after this can fail; the number is the part that must not be lost.
		phones.storePendingPhone(quoteRequestId, to);

		String token = resumeTokens.issueFor(quoteRequestId);
		String body = templates.render(TemplateCode.ESTIMATE_SMS, Map.of(
				"range", range.formatted(),
				// §7: QR and SMS links both land on /resume/{token}. The session cookie is on the device
				// the form was filled in on, which is not the one holding the phone.
				"link", webBaseUrl + "/devam/" + token));

		Optional<String> providerRef = sender.send(to, body);
		notifications.record(quoteRequestId, TemplateCode.ESTIMATE_SMS, to, providerRef);
	}
}
