package com.burakpadr.decorating.quoting.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The internal tool's endpoint (workflow §12, increment 1): a job typed in by hand, priced against the
 * live list, with the breakdown that lets the business argue with the figure.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class QuoteCalculationControllerTest {

	private static final String WORKED_EXAMPLE = """
			{"districtCode":"KADIKOY","area":92,"areaBasis":"NET","layout":"THREE_PLUS_ONE",
			 "scope":"WHOLE_HOME","wallCondition":"MINOR","furnishing":"FURNISHED","doorCount":8,
			 "doorColourChange":true,"hasElevator":true}
			""";

	@Autowired
	private MockMvc mvc;

	@Test
	@DisplayName("a price calculation is not readable without an operator login")
	void refusesAnonymousRequests() throws Exception {
		mvc.perform(post("/api/op/price-calculations")
						.contentType(MediaType.APPLICATION_JSON).content(WORKED_EXAMPLE))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	@DisplayName("the answer carries the price and everything assumed to reach it")
	void answersWithThePriceAndItsAssumptions() throws Exception {
		mvc.perform(post("/api/op/price-calculations")
						.contentType(MediaType.APPLICATION_JSON).content(WORKED_EXAMPLE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.priceBookVersion").value("REAL-2026-01"))
				.andExpect(jsonPath("$.netArea").value(92.00))
				.andExpect(jsonPath("$.areaWasGross").value(false))
				.andExpect(jsonPath("$.rooms.length()").value(7))
				.andExpect(jsonPath("$.rooms[0].label").value("Salon"))
				.andExpect(jsonPath("$.photoCount").value(28))
				.andExpect(jsonPath("$.totalCost").value(50009.39))
				.andExpect(jsonPath("$.billableDays").value(3))
				.andExpect(jsonPath("$.bandRatio").value(0.12))
				.andExpect(jsonPath("$.lines[?(@.code=='WALL_PAINT')].unit")
						.value(org.hamcrest.Matchers.hasItem("SQM")))
				.andExpect(jsonPath("$.lines.length()").value(6));
	}

	@Test
	@WithMockUser
	@DisplayName("an omitted flag means no, rather than rejecting the whole request")
	void treatsAnOmittedFlagAsNo() throws Exception {
		// No "rush", no "doorCountEstimated": a record with primitive booleans answers this with a parse
		// error about null, which tells the caller nothing about what to send.
		mvc.perform(post("/api/op/price-calculations")
						.contentType(MediaType.APPLICATION_JSON).content(WORKED_EXAMPLE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.bandRatio")
						.value(0.12));
	}

	@Test
	@WithMockUser
	@DisplayName("a gross area is converted and the answer says it was")
	void reportsAConvertedArea() throws Exception {
		mvc.perform(post("/api/op/price-calculations")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"districtCode":"KADIKOY","area":112,"areaBasis":"GROSS",
								 "layout":"THREE_PLUS_ONE","scope":"WHOLE_HOME","wallCondition":"MINOR",
								 "furnishing":"FURNISHED","doorCount":8,"hasElevator":true}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.netArea").value(91.84))
				.andExpect(jsonPath("$.areaWasGross").value(true))
				.andExpect(jsonPath("$.bandRatio").value(0.17));
	}

	@Test
	@WithMockUser
	@DisplayName("an area of zero is refused before anything is priced")
	void refusesAnAreaOfZero() throws Exception {
		mvc.perform(post("/api/op/price-calculations")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"districtCode":"KADIKOY","area":0,"areaBasis":"NET",
								 "layout":"THREE_PLUS_ONE","scope":"WHOLE_HOME","wallCondition":"GOOD",
								 "furnishing":"EMPTY","doorCount":0,"hasElevator":true}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser
	@DisplayName("a layout the price book has no rooms for is a bad request, not a 500")
	void refusesAnUnknownLayout() throws Exception {
		mvc.perform(post("/api/op/price-calculations")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"districtCode":"KADIKOY","area":92,"areaBasis":"NET","layout":"VILLA",
								 "scope":"WHOLE_HOME","wallCondition":"GOOD","furnishing":"EMPTY",
								 "doorCount":0,"hasElevator":true}
								"""))
				.andExpect(status().isBadRequest());
	}
}
