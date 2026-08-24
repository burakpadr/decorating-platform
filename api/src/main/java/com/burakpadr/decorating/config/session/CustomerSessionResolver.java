package com.burakpadr.decorating.config.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Turns the session cookie into a {@link CustomerSession}, or refuses the request with 401 (§7).
 *
 * <p>The same cookie {@link OwnedQuoteRequestResolver} reads, and deliberately less: there is no path
 * id to compare it against here, so this resolver can only say who is asking. What is being asked for
 * is checked where the rows are, and a handler that declares this parameter and then forgets to pass
 * it on is the one thing this cannot prevent — {@code QuoteRequestEndpointsAreOwnedTest} covers the
 * declaring, and the service's own tests cover the passing on.
 */
public class CustomerSessionResolver implements HandlerMethodArgumentResolver {

	private final AnonymousSessionCookie cookie;

	public CustomerSessionResolver(AnonymousSessionCookie cookie) {
		this.cookie = cookie;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return CustomerSession.class.equals(parameter.getParameterType());
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
		return new CustomerSession(session);
	}

	private String read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		return Arrays.stream(cookies)
				.filter(candidate -> AnonymousSessionCookie.NAME.equals(candidate.getName()))
				.map(Cookie::getValue)
				.findFirst()
				.orElse(null);
	}
}
