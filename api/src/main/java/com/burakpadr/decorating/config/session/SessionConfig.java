package com.burakpadr.decorating.config.session;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the anonymous session (§7, BOYA-24).
 *
 * <p>{@code decorating.session.secret} has no default. A default would be a published secret, and a
 * published secret signing this cookie means anybody can mint a session for any quote request — so the
 * application refuses to start without one rather than starting insecure. {@code make dev-api} passes a
 * local value; deployment passes a real one.
 */
@Configuration
class SessionConfig implements WebMvcConfigurer {

	private final AnonymousSessionCookie cookie;

	SessionConfig(
			@Value("${decorating.session.secret}") String secret,
			@Value("${decorating.session.ttl:P14D}") Duration ttl,
			@Value("${decorating.session.cookie-domain:}") String domain) {
		// The clock is the system's: unlike the pricing engine, nothing here is asserted against a fixed
		// instant in production, and the tests build their own instance with a fixed clock.
		this.cookie = new AnonymousSessionCookie(secret, ttl, domain, Clock.systemUTC());
	}

	@Bean
	AnonymousSessionCookie anonymousSessionCookie() {
		return cookie;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new OwnedQuoteRequestResolver(cookie));
		// The photo routes (§7) name a photograph rather than a request, so they get the caller's own
		// id and check the row against it themselves.
		resolvers.add(new CustomerSessionResolver(cookie));
	}
}
