package com.burakpadr.decorating.quoting.adapter.in.web;

import com.burakpadr.decorating.quoting.domain.model.CaptureState;
import com.burakpadr.decorating.quoting.domain.model.PhotoRole;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * Where the capture has got to (workflow §2.4, BOYA-42).
 *
 * <p>The totals are sent rather than left to the client to add up. They are the sentence the screen
 * shows — "3 / 28 fotoğraf" — and the same arithmetic done twice on two sides of a contract is exactly
 * what disagrees quietly the day one side learns about close-ups. Which has now happened: {@code
 * extras} carries them, and none of the three numbers moves when one is taken.
 */
record CaptureStateResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CaptureAreaResponse> areas,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Frames §2.4 asks for in total")
		int required,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Frames that have arrived")
		int taken,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean complete) {

	static CaptureStateResponse of(CaptureState state) {
		return new CaptureStateResponse(
				state.areas().stream().map(CaptureAreaResponse::of).toList(),
				state.required(), state.taken(), state.complete());
	}

	record CaptureAreaResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) RoomType type,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Turkish, derived server-side")
			String label,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sortOrder,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CaptureFrameResponse> frames,

			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "§2.6's close-ups: unlimited, skippable, counted towards nothing")
			List<CaptureExtraResponse> extras,

			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Every required frame is in. Close-ups do not affect it either way.")
			boolean complete) {

		static CaptureAreaResponse of(CaptureState.CaptureArea area) {
			return new CaptureAreaResponse(area.id(), area.type(), area.label(), area.sortOrder(),
					area.frames().stream().map(CaptureFrameResponse::of).toList(),
					area.extras().stream().map(CaptureExtraResponse::of).toList(),
					area.complete());
		}
	}

	/** A close-up of a crack or a stain (workflow §2.6). */
	record CaptureExtraResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID photoId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean lowQualityFlag) {

		static CaptureExtraResponse of(CaptureState.CaptureExtra extra) {
			return new CaptureExtraResponse(extra.photoId(), extra.lowQualityFlag());
		}
	}

	record CaptureFrameResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PhotoRole role,

			@Schema(description = "The photograph, if one has arrived. Delete it to retake the frame.")
			UUID photoId,

			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "A reservation nobody uploaded is not taken")
			boolean taken,

			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Kept despite its score — §9 stops arguing after three attempts")
			boolean lowQualityFlag) {

		static CaptureFrameResponse of(CaptureState.CaptureFrame frame) {
			return new CaptureFrameResponse(frame.role(), frame.photoId(), frame.taken(),
					frame.lowQualityFlag());
		}
	}
}
