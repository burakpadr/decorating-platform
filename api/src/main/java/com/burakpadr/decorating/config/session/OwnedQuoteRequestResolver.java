package com.burakpadr.decorating.config.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Turns the session cookie plus the {@code {id}} in the path into an {@link OwnedQuoteRequest}, or
 * refuses the request (§7).
 *
 * <p>Three ways to fail and two answers. No cookie, or one that does not verify, is 401: start again.
 * A cookie that verifies but names a different request is 403, with nothing in the body about which
 * request that was — the caller already knows the id it asked for, and confirming that some other
 * session owns it would tell it something it should not learn.
 */
public class OwnedQuoteRequestResolver implements HandlerMethodArgumentResolver {

	/** The path variable §7's routes use. */
	static final String ID_VARIABLE = "id";

	private final AnonymousSessionCookie cookie;

	public OwnedQuoteRequestResolver(AnonymousSessionCookie cookie) {
		this.cookie = cookie;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return OwnedQuoteRequest.class.equals(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		if (request == null) {
			throw new SessionRequired("no request to read a session from");
		}

		UUID session = cookie.verify(read(request))
				.orElseThrow(() -> new SessionRequired(
						"this request needs the session cookie it was started with"));
		UUID asked = pathId(request);

		if (!session.equals(asked)) {
			throw new NotYourQuoteRequest("the session does not belong to this quote request");
		}
		return new OwnedQuoteRequest(session);
	}

	private String read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		return java.util.Arrays.stream(cookies)
				.filter(candidate -> AnonymousSessionCookie.NAME.equals(candidate.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);
	}

	private UUID pathId(HttpServletRequest request) {
		@SuppressWarnings("unchecked")
		Map<String, String> variables = (Map<String, String>)
				request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
		String raw = variables == null ? null : variables.get(ID_VARIABLE);
		return Optional.ofNullable(raw)
				.map(value -> {
					try {
						return UUID.fromString(value);
					} catch (IllegalArgumentException notAUuid) {
						// A path that cannot hold an id cannot be owned, and answering 400 here would let a
						// caller tell "no such request" apart from "not yours" by the shape of the id.
						throw new NotYourQuoteRequest("not a quote request id");
					}
				})
				.orElseThrow(() -> new IllegalStateException(
						"OwnedQuoteRequest was declared on a handler with no {" + ID_VARIABLE
								+ "} in its path — the parameter cannot mean anything there"));
	}
}
