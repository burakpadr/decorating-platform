package com.burakpadr.decorating.config;

import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Three separate realms, as specified in §7:
 *
 * <ul>
 *   <li>anonymous — a signed httpOnly cookie bound to {@code quote_request.id}
 *   <li>verified — a short-lived token issued after OTP verification
 *   <li>operator — basic authentication, a realm of its own
 * </ul>
 *
 * <p>Only the chain split is wired here. The anonymous cookie filter and the verified-token filter are
 * not implemented yet; until they are, {@code /api/op/**} is the only guarded surface.
 */
@Configuration
public class SecurityConfig {

	/**
	 * Origins allowed to call the API from a browser. Empty in production, where Caddy puts the web app
	 * and the API on one origin and CORS never enters the picture; set in local development, where they
	 * are two ports and every panel request is cross-origin.
	 */
	@Value("${decorating.cors.allowed-origins:}")
	private List<String> allowedOrigins = List.of();

	/** Operator realm. Separate chain so operator auth never leaks into the public API. */
	@Bean
	@Order(1)
	SecurityFilterChain operatorChain(HttpSecurity http) throws Exception {
		return http
				.securityMatcher("/api/op/**")
				.csrf(csrf -> csrf.disable())
				.cors(c -> c.configurationSource(corsConfiguration()))
				// No session: the panel authenticates every call. A JSESSIONID handed out by a rejected
				// request is a session nothing will ever use.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// The ERROR dispatch has to be allowed through. Without it, the 401 the entry point sends
				// is forwarded to /error, that forward is authorised again, denied, and the browser ends up
				// with 403 — which is exactly what the running application did while MockMvc reported 401,
				// because MockMvc never performs the internal dispatch.
				.authorizeHttpRequests(auth -> auth
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.anyRequest().authenticated())
				// The entry point is explicit because the default answered 403 with a WWW-Authenticate
				// header — a combination no browser acts on. A browser prompts for credentials on 401 and
				// never on 403, so the panel had no way to log in and OperatorRealmHttpTest is what says so.
				.httpBasic(basic -> basic.authenticationEntryPoint(operatorEntryPoint()))
				.build();
	}

	/**
	 * Public API. Customer-scoped authorisation is per-request (the cookie or token must match the quote
	 * request being touched), not URL-pattern based, so these paths stay open at this level.
	 */
	@Bean
	@Order(2)
	SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
		return http
				.csrf(csrf -> csrf.disable())
				.cors(c -> c.configurationSource(corsConfiguration()))
				.authorizeHttpRequests(auth -> auth
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers("/api/**").permitAll()
						.anyRequest().denyAll())
				.build();
	}

	private BasicAuthenticationEntryPoint operatorEntryPoint() {
		BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
		entryPoint.setRealmName("operator");
		return entryPoint;
	}

	/**
	 * Not a bean: Spring MVC publishes a {@code CorsConfigurationSource} of its own
	 * ({@code mvcHandlerMappingIntrospector}), and a second one turns the chain's parameter into an
	 * ambiguous injection. The chains that need it call this.
	 */
	private CorsConfigurationSource corsConfiguration() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		if (allowedOrigins.isEmpty()) {
			return source;                 // no mapping, no CORS headers, nothing to get wrong
		}

		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		// The operator's credentials and the anonymous session cookie both ride on the request, so the
		// response has to say credentials are allowed — which is why the origin list can never be "*".
		configuration.setAllowCredentials(true);
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}
}
