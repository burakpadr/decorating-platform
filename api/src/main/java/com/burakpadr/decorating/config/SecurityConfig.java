package com.burakpadr.decorating.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Three separate realms, as specified in §7:
 *
 * <ul>
 *   <li>anonymous — a signed httpOnly cookie bound to {@code quote_request.id}
 *   <li>verified — a short-lived token issued after OTP verification
 *   <li>operator — form or basic login, a realm of its own
 * </ul>
 *
 * <p>Only the chain split is wired here. The anonymous cookie filter and the verified-token filter
 * are not implemented yet; until they are, {@code /api/op/**} is the only guarded surface.
 */
@Configuration
public class SecurityConfig {

	/** Operator realm. Separate chain so operator auth never leaks into the public API. */
	@Bean
	@Order(1)
	SecurityFilterChain operatorChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher("/api/op/**")
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
				.httpBasic(basic -> {})
				.build();
	}

	/**
	 * Public API. Customer-scoped authorisation is per-request (the cookie or token must match the
	 * quote request being touched), not URL-pattern based, so these paths stay open at this level.
	 */
	@Bean
	@Order(2)
	SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers("/api/**").permitAll()
						.anyRequest().denyAll())
				.build();
	}
}
