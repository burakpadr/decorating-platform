package com.burakpadr.decorating.quoting.application;

import com.burakpadr.decorating.quoting.domain.model.Photo;
import com.burakpadr.decorating.quoting.domain.model.PhotoNotFound;
import com.burakpadr.decorating.quoting.domain.model.PresignedUrl;
import com.burakpadr.decorating.quoting.domain.port.in.ReviewPhotos;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoRepository;
import com.burakpadr.decorating.quoting.domain.port.out.PhotoStorage;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The operator's read (§9, BOYA-40).
 *
 * <p>No ownership check, on purpose: the operator realm is the check, and it is a different one — an
 * operator reviews everybody's photographs, which is the job. What is not different is the TTL: the
 * URL is signed for minutes because the screen it is made for lives for minutes, and a link that
 * outlives the screen is a link that can be forwarded.
 */
@Service
class PhotoReviewService implements ReviewPhotos {

	private final PhotoRepository photos;
	private final PhotoStorage storage;

	PhotoReviewService(PhotoRepository photos, PhotoStorage storage) {
		this.photos = photos;
		this.storage = storage;
	}

	@Override
	@Transactional(readOnly = true)
	public PresignedUrl readable(UUID photoId) {
		Photo photo = photos.findById(photoId)
				.orElseThrow(() -> new PhotoNotFound(String.valueOf(photoId)));
		return storage.presignGet(photo.storageKey());
	}
}
