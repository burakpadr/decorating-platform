package com.burakpadr.decorating.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The identifier layout every row in the schema depends on. Pure JDK, no Spring: a key generator that
 * needed a context would be a key generator nothing could unit-test.
 */
class Uuid7Test {

	@Test
	@DisplayName("it is a version 7, variant 2 UUID")
	void carriesTheRightVersionAndVariant() {
		UUID id = Uuid7.generate();

		assertThat(id.version()).isEqualTo(7);
		assertThat(id.variant()).isEqualTo(2);
	}

	@Test
	@DisplayName("the first 48 bits are the millisecond it was made, so keys sort by time")
	void isTimeOrdered() {
		UUID early = Uuid7.generate(1_700_000_000_000L);
		UUID later = Uuid7.generate(1_700_000_000_001L);

		assertThat(timestampOf(early)).isEqualTo(1_700_000_000_000L);
		assertThat(timestampOf(later)).isGreaterThan(timestampOf(early));
	}

	@Test
	@DisplayName("ids made in the same millisecond are still unique and unguessable")
	void isUniqueWithinAMillisecond() {
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < 10_000; i++) {
			ids.add(Uuid7.generate(1_700_000_000_000L));
		}

		Set<UUID> distinct = new HashSet<>(ids);
		assertThat(distinct)
				.as("a collision would silently overwrite a row — 74 random bits per millisecond")
				.hasSize(ids.size());
	}

	private static long timestampOf(UUID id) {
		return id.getMostSignificantBits() >>> 16 & 0xFFFF_FFFF_FFFFL;
	}
}
