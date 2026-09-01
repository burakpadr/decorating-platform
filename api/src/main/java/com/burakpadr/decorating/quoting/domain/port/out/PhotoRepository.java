package com.burakpadr.decorating.quoting.domain.port.out;

import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code photo} rows (§4.3).
 *
 * <p>One at a time, unlike {@code room}: frames arrive as the customer walks through the home and each
 * one is uploaded the moment it is confirmed (§2.7), because 28 photographs sent at the end is 28
 * photographs lost when the connection drops.
 */
public interface PhotoRepository {

	void save(Photo photo);

	Optional<Photo> findById(UUID photoId);

	/** The frame already held for this role, if there is one — §4.3 allows a second only for DETAIL. */
	Optional<Photo> findByRoomAndRole(UUID roomId, PhotoRole role);

	/**
	 * Every photograph of every room of this request, in no particular order.
	 *
	 * <p>Reserved rows included. Whether an intent nobody uploaded counts as a photograph is a question
	 * about the capture, not about storage, so it is answered above this — see {@code CaptureState}.
	 */
	List<Photo> findByQuoteRequest(UUID quoteRequestId);

	void delete(UUID photoId);

	/** Which request this photograph belongs to, by way of its room. Empty when there is no such row. */
	Optional<UUID> quoteRequestOf(UUID photoId);
}
