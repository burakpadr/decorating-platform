package com.burakpadr.decorating.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A Turkish mobile number, normalised (§2's shared package, §4.1's unique phone column).
 *
 * <p>Normalisation is not tidiness. {@code customer.phone} is UNIQUE and lookup by phone is how a
 * returning customer resolves to their existing row (§4.1), so the same person typing 0555… on Monday
 * and +90 555… on Friday has to land on one row. Two rows for one phone is a repeat customer the
 * business cannot see, and — once OTP exists — a verified number that unlocks nothing.
 *
 * <p>Rate limiting keys off the same string (§4.6's {@code scope_key} is literally {@code "phone:+9053…"}),
 * so a number that normalises two ways is a limit that counts to twice.
 */
class PhoneNumberTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"05551234567",          // as it is written on a business card
			"5551234567",           // as it is typed when the leading zero feels redundant
			"+905551234567",        // as it arrives from a phone's own contact list
			"905551234567",         // as some providers hand it back
			"0555 123 45 67",       // as it is read out loud
			"0555-123-45-67",
			"(0555) 123 45 67",
			" 0555 123 45 67 ",
	})
	@DisplayName("every way one number gets typed comes out as the same number")
	void normalisesEveryCommonForm(String typed) {
		assertThat(PhoneNumber.of(typed).e164()).isEqualTo("+905551234567");
	}

	@Test
	@DisplayName("two spellings of one number are one value")
	void equalityIsByNumberNotBySpelling() {
		assertThat(PhoneNumber.of("0555 123 45 67")).isEqualTo(PhoneNumber.of("+905551234567"));
		assertThat(PhoneNumber.of("0555 123 45 67")).hasSameHashCodeAs(PhoneNumber.of("905551234567"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"02121234567",          // İstanbul landline: a real number, and not one an SMS reaches
			"04441234567",          // a service line
			"0555123456",           // one digit short
			"055512345678",         // one too many
			"+15551234567",         // not Turkey
			"0555abc4567",
			"",
			"   ",
	})
	@DisplayName("what is not a Turkish mobile number is refused, and named as such")
	void refusesWhatCannotReceiveAnSms(String typed) {
		assertThatThrownBy(() -> PhoneNumber.of(typed))
				.as("%s", typed)
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("null is refused rather than becoming an empty number")
	void refusesNull() {
		assertThatThrownBy(() -> PhoneNumber.of(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("a stored number is read back without being re-validated into an error")
	void roundTripsThroughItsStoredForm() {
		PhoneNumber phone = PhoneNumber.of("0555 123 45 67");

		assertThat(PhoneNumber.of(phone.e164())).isEqualTo(phone);
	}

	@Test
	@DisplayName("it shows itself masked, because a log is not a place for a phone number")
	void masksItselfForDisplay() {
		// The notification row keeps the real one — it is how a customer who says "I got no SMS" is
		// answered. Logs and error messages get this instead.
		assertThat(PhoneNumber.of("05551234567").masked()).isEqualTo("+90 555 *** ** 67");
	}

	@Test
	@DisplayName("toString does not leak the number either")
	void toStringIsMasked() {
		assertThat(PhoneNumber.of("05551234567")).hasToString("+90 555 *** ** 67");
	}
}
