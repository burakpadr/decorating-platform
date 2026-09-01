package com.burakpadr.decorating.quoting.domain.model;

/**
 * What a rate limit is counting (§4.6's {@code rate_limit_bucket_check}).
 *
 * <p>Mirrors the CHECK constraint rather than a subset of it: the column decides which values a row
 * may hold, and an enum that quietly knew fewer would turn a database refusal into a surprise at the
 * one moment the system is under attack.
 */
public enum RateBucket {

	/** §11's analysis quota: loose, and never a rejection — a request over it is flagged and queued. */
	ANALYSIS,

	/** §11's OTP limits: strict, because every message costs money. */
	OTP,

	/** Collecting a phone number for the waitlist, which is an abuse vector even with no SMS. */
	WAITLIST
}
