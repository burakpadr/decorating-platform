package com.burakpadr.decorating.config.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The anonymous session's token (§7, BOYA-24).
 *
 * <p>Stage 1 creates no customer row (§4.1), so the only thing tying a browser to its quote request is
 * this cookie. That makes it the whole access control for a stranger's answers, phone number and photos,
 * and every test here is a way somebody could try to read a request that is not theirs.
 *
 * <p>No Spring context: the token is arithmetic over a secret and a clock, and it should be testable at
 * that speed.
 */
class AnonymousSessionCookieTest {

	private static final String SECRET = "a-secret-nobody-else-has-and-it-is-long-enough";
	private static final Instant NOW = Instant.parse("2026-08-21T09:00:00Z");
	private static final Duration TTL = Duration.ofDays(14);

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
	private final AnonymousSessionCookie cookie = new AnonymousSessionCookie(SECRET, TTL, "", clock);

	private final UUID id = UUID.fromString("01930000-0000-7000-8000-0000000000aa");

	@Test
	@DisplayName("a minted token reads back as the request it was minted for")
	void roundTrips() {
		assertThat(cookie.verify(cookie.mint(id))).contains(id);
	}

	@Test
	@DisplayName("a token signed with another secret is refused")
	void refusesAForeignSignature() {
		AnonymousSessionCookie attacker =
				new AnonymousSessionCookie("some-other-secret-entirely-different", TTL, "", clock);

		assertThat(cookie.verify(attacker.mint(id)))
				.as("without this the cookie is a plain text id and anyone can name any request")
				.isEmpty();
	}

	@Test
	@DisplayName("swapping the id while keeping the signature is refused")
	void refusesASwappedId() {
		String honest = cookie.mint(id);
		UUID other = UUID.fromString("01930000-0000-7000-8000-0000000000bb");
		String forged = honest.replace(id.toString(), other.toString());

		assertThat(forged).isNotEqualTo(honest);
		assertThat(cookie.verify(forged))
				.as("the id is inside what is signed, not beside it")
				.isEmpty();
	}

	@Test
	@DisplayName("extending the expiry while keeping the signature is refused")
	void refusesAStretchedExpiry() {
		String honest = cookie.mint(id);
		String[] parts = honest.split("\\.");
		String forged = parts[0] + "." + parts[1] + "." + (Long.parseLong(parts[2]) + 86_400) + "."
				+ parts[3];

		assertThat(cookie.verify(forged)).isEmpty();
	}

	@Test
	@DisplayName("a token stops working when it runs out, and one second before it still does")
	void expires() {
		String token = cookie.mint(id);

		AnonymousSessionCookie justInTime = at(NOW.plus(TTL).minusSeconds(1));
		AnonymousSessionCookie tooLate = at(NOW.plus(TTL).plusSeconds(1));

		assertThat(justInTime.verify(token)).contains(id);
		assertThat(tooLate.verify(token))
				.as("§8 keeps photos for 30 days and quotes valid for 14; a cookie that never expires "
						+ "outlives both")
				.isEmpty();
	}

	@Test
	@DisplayName("nothing that is not a token is a token")
	void refusesRubbish() {
		for (String rubbish : new String[] {
				"", "   ", id.toString(), "1." + id + ".x.y", "1..0.sig", "2." + id + ".0.sig",
				cookie.mint(id) + ".extra", "1.not-a-uuid.99999999999.sig"}) {
			assertThat(cookie.verify(rubbish)).as("%s", rubbish).isEmpty();
		}
		assertThat(cookie.verify(null)).isEmpty();
	}

	@Test
	@DisplayName("two tokens for the same request are identical, so a refresh does not churn cookies")
	void isDeterministic() {
		assertThat(cookie.mint(id)).isEqualTo(cookie.mint(id));
	}

	@Test
	@DisplayName("the Set-Cookie header says httpOnly, Lax and Secure, and carries no domain in dev")
	void describesItsOwnCookie() {
		var built = cookie.asCookie(id);

		assertThat(built.getName()).isEqualTo("dp_session");
		assertThat(built.isHttpOnly())
				.as("a session readable from JavaScript is one an injected script can post elsewhere")
				.isTrue();
		assertThat(built.getSameSite()).isEqualTo("Lax");
		assertThat(built.isSecure()).isTrue();
		assertThat(built.getPath()).isEqualTo("/");
		assertThat(built.getDomain())
				.as("empty in dev: a host-only cookie still reaches :8080 from a page on :3000, because "
						+ "cookies ignore the port and same-site does too")
				.isNull();
		assertThat(built.getMaxAge()).isEqualTo(TTL);
	}

	@Test
	@DisplayName("a configured domain is shared across subdomains, which is why §7 wants one origin")
	void carriesTheConfiguredDomain() {
		AnonymousSessionCookie hosted =
				new AnonymousSessionCookie(SECRET, TTL, "example.com", clock);

		assertThat(hosted.asCookie(id).getDomain()).isEqualTo("example.com");
	}

	private AnonymousSessionCookie at(Instant instant) {
		return new AnonymousSessionCookie(SECRET, TTL, "", Clock.fixed(instant, ZoneOffset.UTC));
	}

	@Test
	@DisplayName("a blank secret is refused at startup, not at the first request")
	void refusesABlankSecret() {
		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> new AnonymousSessionCookie("  ", TTL, "", clock))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("decorating.session.secret");
	}

	@Test
	@DisplayName("a short secret is refused: a guessable key is the same as no signature")
	void refusesAShortSecret() {
		Optional<String> unused = Optional.empty();

		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> new AnonymousSessionCookie("short", TTL, "", clock))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(unused).isEmpty();
	}
}
