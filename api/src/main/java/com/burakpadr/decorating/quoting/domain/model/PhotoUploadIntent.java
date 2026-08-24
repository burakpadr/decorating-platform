package com.burakpadr.decorating.quoting.domain.model;

/**
 * A reserved row and the URL that fills it (§9, workflow §2.7).
 *
 * <p>The two travel together because they are one answer: the id is what the client will complete or
 * delete the frame by, and the URL is where the bytes go. Handing back only the URL would leave the
 * browser holding an upload it cannot report, which is how a photograph ends up in the bucket with
 * nothing naming it.
 */
public record PhotoUploadIntent(Photo photo, PresignedUrl upload) {}
