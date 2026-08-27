/**
 * Outbound adapter for the versioned consent notices.
 *
 * <p>Its own package rather than a file read inside persistence: the notice is not a row and must not
 * become one. Decision 0018 explains why the text ships with the application.
 */
package com.burakpadr.decorating.quoting.adapter.out.notice;
