package com.burakpadr.decorating.quoting.adapter.out.vision;

import com.burakpadr.decorating.quoting.domain.model.CeilingFinding;
import com.burakpadr.decorating.quoting.domain.model.Coating;
import com.burakpadr.decorating.quoting.domain.model.CrackLevel;
import com.burakpadr.decorating.quoting.domain.model.FillerBand;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Moisture;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysis;
import com.burakpadr.decorating.quoting.domain.model.RoomAnalysisRequest;
import com.burakpadr.decorating.quoting.domain.model.SurfaceFinding;
import com.burakpadr.decorating.quoting.domain.model.Tone;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * A validated response, turned into findings (§4.4, §6).
 *
 * <p>Only ever called with a response the schema has already accepted, which is what lets this read
 * fields without asking whether they are there. Every {@code valueOf} below is safe for the same
 * reason: the vocabularies are the domain's, checked against the schema by
 * {@code RoomAnalysisSchemaTest} and against the database by the same test.
 *
 * <p>It maps all of it or none of it. There is no partial path through this method — the record it
 * builds refuses to exist incomplete — because a room whose walls parsed and whose ceiling did not
 * would be priced as a room with a sound ceiling, and nothing on the row would say otherwise.
 */
final class RoomAnalysisMapper {

	private RoomAnalysisMapper() {}

	static RoomAnalysis map(
			RoomAnalysisRequest request,
			JsonNode response,
			String rawResponse,
			String promptVersion,
			String modelVersion) {

		JsonNode ceiling = response.get("ceiling");

		return new RoomAnalysis(
				// The room the frames came from. What the model thinks the room is stays in the raw
				// response: room.room_type is the customer's answer and §5.3 prices that one (§4.4 gives
				// the row no column for the model's reading either).
				request.roomId(),
				promptVersion,
				modelVersion,
				rawResponse,
				surfaces(response.get("surfaces")),
				new CeilingFinding(
						Moisture.valueOf(ceiling.get("staining").stringValue()),
						FillerBand.valueOf(ceiling.get("fillerRatio").stringValue())),
				// Kept out of CeilingFinding: the engine takes that record and prices neither this nor
				// anything derived from it. How well the ceiling was read belongs to the room's
				// confidence (§6, decision 0021), which is not the engine's business.
				ceiling.get("confidence").decimalValue(),
				ceiling.get("cornice").booleanValue(),
				ceiling.get("downlightCount").intValue(),
				Furnishing.valueOf(response.get("furnishing").stringValue()),
				response.get("doorCount").intValue(),
				response.get("windowCount").intValue(),
				response.get("radiatorCount").intValue(),
				response.get("confidence").decimalValue(),
				strings(response.get("unusablePhotos")),
				strings(response.get("notes")));
	}

	private static List<SurfaceFinding> surfaces(JsonNode surfaces) {
		List<SurfaceFinding> findings = new ArrayList<>(surfaces.size());
		for (JsonNode surface : surfaces) {
			findings.add(new SurfaceFinding(
					surface.get("id").stringValue(),
					Coating.valueOf(surface.get("coating").stringValue()),
					Tone.valueOf(surface.get("currentTone").stringValue()),
					FillerBand.valueOf(surface.get("fillerRatio").stringValue()),
					surface.get("skimCoatRequired").booleanValue(),
					CrackLevel.valueOf(surface.get("crackLevel").stringValue()),
					Moisture.valueOf(surface.get("moisture").stringValue()),
					surface.get("wallpaper").booleanValue(),
					surface.get("confidence").decimalValue()));
		}
		return findings;
	}

	private static List<String> strings(JsonNode array) {
		return array.valueStream().map(JsonNode::stringValue).toList();
	}
}
