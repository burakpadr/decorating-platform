package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.config.session.NotYourQuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.CapturedFrame;
import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoNotFound;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.PhotoUploadIntent;
import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequestNotFound;
import com.burakpadr.decorating.quoting.domain.port.in.CapturePhotos;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoRepository;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoStorage;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.quoting.domain.port.out.RoomRepository;
import com.burakpadr.decorating.shared.Uuid7;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserving, completing and retaking a frame (§9, workflow §2.4–2.7, BOYA-40).
 *
 * <p>The bytes are not here and never will be: the browser holds a presigned URL and PUTs straight to
 * storage. What this service decides is everything around them — whose room this is, whether the
 * request is at a point where photographs mean anything, and whether the frame being asked for is one
 * a room may hold twice.
 *
 * <p>Ownership is checked against the session's request rather than the id in the path, every time. A
 * photo id is a row, not a credential, and the alternative is an operator opening a stranger's
 * bathroom because two customers were photographing at once.
 */
@Service
class PhotoCaptureService implements CapturePhotos {

	private final PhotoRepository photos;
	private final RoomRepository rooms;
	private final QuoteRequestRepository requests;
	private final PhotoStorage storage;

	/** UTC, as {@code SessionConfig} does it: the column is {@code timestamptz} and nothing here is
	 * scheduled, so there is no zone to be wrong about. */
	private final Clock clock = Clock.systemUTC();

	PhotoCaptureService(PhotoRepository photos, RoomRepository rooms, QuoteRequestRepository requests,
			PhotoStorage storage) {
		this.photos = photos;
		this.rooms = rooms;
		this.requests = requests;
		this.storage = storage;
	}

	@Override
	@Transactional
	public PhotoUploadIntent intend(UUID quoteRequestId, UUID roomId, PhotoRole role) {
		// The room decides the answer: an id that is not ours and an id that does not exist are the same
		// refusal, or the endpoint becomes a way to find out which rooms exist.
		if (!rooms.quoteRequestOf(roomId).filter(quoteRequestId::equals).isPresent()) {
			throw new NotYourQuoteRequest("this room belongs to another quote request");
		}
		requireCapturing(quoteRequestId);

		if (!role.isRepeatable()) {
			supersede(photos.findByRoomAndRole(roomId, role), role);
		}

		Photo intent = Photo.intended(Uuid7.generate(), quoteRequestId, roomId, role);
		photos.save(intent);
		PresignedUrl upload = storage.presignPut(intent.storageKey());
		return new PhotoUploadIntent(intent, upload);
	}

	/**
	 * Clears a reservation nobody used, and refuses one that was.
	 *
	 * <p>An intent whose PUT never happened is the ordinary case — a lift with no signal, a closed tab —
	 * and colliding with it would leave the customer stuck on one frame with no way forward they could
	 * find. A frame that did arrive is different: replacing it silently would leave the old object in
	 * the bucket with no row naming it, which is an object {@code PhotoPurge} can never find either.
	 */
	private void supersede(Optional<Photo> existing, PhotoRole role) {
		existing.ifPresent(held -> {
			if (held.isUploaded()) {
				throw new IllegalStateException(
						"this frame has already been photographed: delete it before taking " + role + " again");
			}
			storage.delete(held.storageKey());
			photos.delete(held.id());
		});
	}

	@Override
	@Transactional
	public Photo complete(UUID quoteRequestId, UUID photoId, CapturedFrame frame) {
		Photo photo = mine(quoteRequestId, photoId);
		requireCapturing(quoteRequestId);

		Photo uploaded = photo.uploaded(clock.instant(), frame);
		photos.save(uploaded);
		return uploaded;
	}

	@Override
	@Transactional
	public void discard(UUID quoteRequestId, UUID photoId) {
		Photo photo = mine(quoteRequestId, photoId);
		requireCapturing(quoteRequestId);

		// The object first. A row deleted before its object leaves the object unreachable and unnamed,
		// and §12's retention is counted from rows.
		storage.delete(photo.storageKey());
		photos.delete(photo.id());
	}

	private Photo mine(UUID quoteRequestId, UUID photoId) {
		UUID owner = photos.quoteRequestOf(photoId)
				.orElseThrow(() -> new PhotoNotFound(String.valueOf(photoId)));
		if (!owner.equals(quoteRequestId)) {
			throw new NotYourQuoteRequest("this photo belongs to another quote request");
		}
		return photos.findById(photoId).orElseThrow(() -> new PhotoNotFound(String.valueOf(photoId)));
	}

	/**
	 * §3: photographs belong between the agreed room list and the submission.
	 *
	 * <p>Before that there is no list for a frame to belong to; after it the analysis has already read
	 * what was there, and a photograph arriving late would be one the quote was not built on.
	 */
	private void requireCapturing(UUID quoteRequestId) {
		QuoteRequest request = requests.findById(quoteRequestId)
				.orElseThrow(() -> new QuoteRequestNotFound(String.valueOf(quoteRequestId)));
		if (!request.acceptsPhotographs()) {
			throw new IllegalStateException(
					"this request is not collecting photographs: the room list is agreed once and the "
							+ "photographs are taken against it");
		}
	}
}
