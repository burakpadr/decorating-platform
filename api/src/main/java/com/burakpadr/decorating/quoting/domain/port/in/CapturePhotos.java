package com.burakpadr.decorating.quoting.domain.port.in;

import com.burakpadr.decorating.quoting.domain.model.CapturedFrame;
import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.PhotoUploadIntent;
import java.util.UUID;

/**
 * The three things a browser does with a photograph (§7, §9, BOYA-40).
 *
 * <p>Three calls rather than one upload, because the upload is not ours: the browser PUTs the bytes to
 * storage itself and tells us afterwards. Every method takes the quote request the session owns — the
 * photo id in the path names a row, and a row is not proof of who is asking.
 */
public interface CapturePhotos {

	/** Reserves a frame and signs the URL the browser will PUT it to. */
	PhotoUploadIntent intend(UUID quoteRequestId, UUID roomId, PhotoRole role);

	/** The bytes arrived. What the client measured on the way out comes with it (§9). */
	Photo complete(UUID quoteRequestId, UUID photoId, CapturedFrame frame);

	/** A retake, or a reservation nobody used: the row and the object go together (§2.5). */
	void discard(UUID quoteRequestId, UUID photoId);
}
