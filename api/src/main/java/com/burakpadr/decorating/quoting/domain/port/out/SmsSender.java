package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.shared.PhoneNumber;
import java.util.Optional;

/**
 * Handing a message to a provider (§13).
 *
 * <p>The return value is the provider's own reference, and it is optional because "nothing sent it" is
 * a real and expected answer: no provider is chosen yet (BOYA-6), so the deployed adapter records the
 * message and stops. Empty means the {@code notification} row stays QUEUED, which is exactly what
 * BOYA-33 asks for — a message nobody sent must not look like one that went.
 */
public interface SmsSender {

	Optional<String> send(PhoneNumber to, String body);
}
