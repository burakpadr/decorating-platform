package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;

/**
 * Where the photographs live, and how a browser is let at them (§9).
 *
 * <p>Only URLs and keys cross this port — never bytes. That is the point of it: "photos never pass
 * through the JVM", so a method here that took an {@code InputStream} would be the design going wrong
 * in one line, and the memory of a 28-photograph capture arriving through the API at once is the shape
 * of the failure.
 */
public interface PhotoStorage {

	/** A URL the customer's browser can PUT one photograph to, and nothing else. */
	PresignedUrl presignPut(String key);

	/**
	 * A short-lived read. Two readers: the operator's review screen (§9), and the vision provider, which
	 * is handed one URL per frame rather than the bytes (§6). Short-lived matters in both — this is a
	 * photograph of somebody's home that anyone holding the link can open.
	 */
	PresignedUrl presignGet(String key);

	/** Removes the object. Quiet when there is nothing there: an intent nobody used leaves no object. */
	void delete(String key);
}
