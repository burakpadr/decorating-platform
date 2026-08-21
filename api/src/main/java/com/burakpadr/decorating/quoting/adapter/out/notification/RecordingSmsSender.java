package com.burakpadr.decorating.quoting.adapter.out.notification;

import com.burakpadr.decorating.quoting.domain.port.out.SmsSender;
import com.burakpadr.decorating.shared.PhoneNumber;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * The adapter that does not send (BOYA-33, pending BOYA-6).
 *
 * <p>No provider has been chosen. What is available to Turkish numbers needs a sender header registered
 * with BTK — a trademark certificate or a domain allocation document — and the international free tiers
 * do not reach Turkey with a branded header at all. So rather than promise a message nobody can send,
 * this records it: the number is kept, the {@code notification} row stays QUEUED, and the rendered body
 * goes to the log where an operator can read it and send it by hand.
 *
 * <p>Returning empty is the contract, not a stub's shrug — {@link SmsSender} treats "nothing sent it" as
 * a real answer, and BOYA-33's acceptance is exactly that: "gönderilmeyen SMS sessizce kaybolmaz".
 *
 * <p>{@code @ConditionalOnMissingBean} so wiring a real provider is one class and no edits here.
 */
@Component
@ConditionalOnMissingBean(name = "smsProvider")
class RecordingSmsSender implements SmsSender {

	private static final Logger log = LoggerFactory.getLogger(RecordingSmsSender.class);

	@Override
	public Optional<String> send(PhoneNumber to, String body) {
		// Masked. The number is in the notification row, where it belongs; a log is read by whoever has
		// the terminal open.
		log.info("SMS not sent (no provider configured) → {} · {}", to.masked(), body);
		return Optional.empty();
	}
}
