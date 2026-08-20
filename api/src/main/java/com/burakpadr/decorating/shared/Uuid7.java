package com.burakpadr.decorating.shared;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7 — a time-ordered identifier, which is what every primary key in this schema is meant to be.
 *
 * <p>Two reasons, both stated in §4 and neither cosmetic. Sequential integers would leak volume and
 * let anyone walk from one customer's quote to the next, so keys are random. Purely random keys
 * scatter index writes across the whole B-tree, so keys are time-prefixed: the first 48 bits are the
 * Unix millisecond, which keeps inserts landing next to each other while the remaining 74 bits keep
 * them unguessable.
 *
 * <p>The JDK has no v7 generator, and this is the only place the layout is written down, so it is
 * written down once. {@code gen_random_uuid()} still appears in two places: migrations, where the application is
 * not running to generate anything, and bulk copies of rows nothing outside their own table
 * references.
 */
public final class Uuid7 {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Uuid7() {}

	/** RFC 9562 §5.7 layout: 48-bit big-endian timestamp, version 7, variant 2, random elsewhere. */
	public static UUID generate() {
		return generate(System.currentTimeMillis());
	}

	static UUID generate(long unixMillis) {
		byte[] random = new byte[10];
		RANDOM.nextBytes(random);

		long high = (unixMillis & 0xFFFF_FFFF_FFFFL) << 16;
		high |= (long) (random[0] & 0x0F) << 8;                 // 4 bits of rand_a, version takes 4
		high |= random[1] & 0xFF;
		high |= 0x7000L;                                        // version 7

		long low = 0;
		for (int i = 2; i < 10; i++) {
			low = (low << 8) | (random[i] & 0xFFL);
		}
		low &= 0x3FFF_FFFF_FFFF_FFFFL;                          // clear the two variant bits
		low |= 0x8000_0000_0000_0000L;                          // variant 2 (RFC 4122/9562)

		return new UUID(high, low);
	}
}
