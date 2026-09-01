package com.burakpadr.decorating.quoting.adapter.out.vision;

import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;

/**
 * One frame on its way to the model: the label the response will name it by, and a short read of it.
 *
 * <p>A presigned GET rather than bytes, for the reason §9 gives about the upload path — the
 * photograph does not travel through the JVM. The lifetime travels with the URL because it is the
 * interesting half: this is a readable photograph of somebody's home for as long as it lasts.
 */
record VisionImage(String label, PresignedUrl read) {}
