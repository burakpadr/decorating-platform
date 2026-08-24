package com.burakpadr.decorating.quoting.adapter.out.storage;

/**
 * Storage could not be reached or could not be configured.
 *
 * <p>Unchecked and named for what it is, because there is nothing the caller can do about it and every
 * answer that pretends otherwise — a null URL, a silently skipped delete — turns an outage into a
 * quote request that looks complete with no photographs behind it.
 */
public class StorageUnavailable extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public StorageUnavailable(String message, Throwable cause) {
		super(message, cause);
	}
}
