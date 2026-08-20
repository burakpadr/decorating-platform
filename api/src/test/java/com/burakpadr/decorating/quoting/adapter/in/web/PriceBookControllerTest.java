package com.burakpadr.decorating.quoting.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The operator's price book endpoints (§7).
 *
 * <p>Behind the operator realm, which is the first thing asserted: these responses carry
 * {@code total_cost} territory — the figures §1 keeps away from customers — and an unauthenticated
 * request must not see a version list at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PriceBookControllerTest {

	private static final String ACTIVE = "REAL-2026-02";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	/** The database is shared with every other test in the suite; leave it as it was found. */
	@AfterEach
	void restoreTheActiveVersion() {
		jdbc.update("DELETE FROM price_book WHERE version_code LIKE 'WEB-%'");
		jdbc.update("DELETE FROM price_book WHERE version_code LIKE 'REAL-2026-0%' "
				+ "AND version_code NOT IN ('REAL-2026-01', 'REAL-2026-02')");
		jdbc.update("UPDATE price_book SET active = false WHERE active = true");
		jdbc.update("UPDATE price_book SET active = true WHERE version_code = ?", ACTIVE);
	}

	@Test
	@DisplayName("the version list is not readable without an operator login")
	void refusesAnonymousRequests() throws Exception {
		mvc.perform(get("/api/op/price-books")).andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	@DisplayName("GET lists the versions, newest first")
	void listsVersions() throws Exception {
		mvc.perform(get("/api/op/price-books"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].versionCode").value(org.hamcrest.Matchers.hasItem(ACTIVE)))
				.andExpect(jsonPath("$[0].createdAt").exists());
	}

	@Test
	@WithMockUser
	@DisplayName("POST clones a version, and the copy arrives switched off")
	void createsAVersionByCloning() throws Exception {
		mvc.perform(post("/api/op/price-books")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceId\":\"" + idOf(ACTIVE) + "\",\"versionCode\":\"WEB-COPY-1\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.versionCode").value("WEB-COPY-1"))
				.andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.id").exists());
	}

	@Test
	@WithMockUser
	@DisplayName("POST /{id}/activate makes that version the one quotes are priced against")
	void activatesAVersion() throws Exception {
		String created = mvc.perform(post("/api/op/price-books")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceId\":\"" + idOf(ACTIVE) + "\",\"versionCode\":\"WEB-COPY-2\"}"))
				.andReturn().getResponse().getContentAsString();
		UUID id = UUID.fromString(created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));

		mvc.perform(post("/api/op/price-books/" + id + "/activate"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));

		mvc.perform(get("/api/op/price-books"))
				.andExpect(jsonPath("$[?(@.versionCode=='" + ACTIVE + "')].active")
						.value(org.hamcrest.Matchers.hasItem(false)));
	}

	@Test
	@WithMockUser
	@DisplayName("POST /{id}/bulk-increase produces a raised version and leaves the live one alone")
	void appliesABulkIncrease() throws Exception {
		mvc.perform(post("/api/op/price-books/" + idOf(ACTIVE) + "/bulk-increase")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"target\":\"LABOUR\",\"percent\":15}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.versionCode").value("REAL-2026-03"))
				.andExpect(jsonPath("$.active").value(false));

		assertThat(jdbc.queryForObject("SELECT labour_cost FROM price_book_item i "
						+ "JOIN price_book b ON b.id = i.price_book_id "
						+ "WHERE b.version_code = ? AND i.code = 'WALL_PAINT'",
						BigDecimal.class, ACTIVE))
				.as("the live list is what quotes are priced against; a zam must not reach it")
				.isEqualByComparingTo("31.25");
	}

	@Test
	@WithMockUser
	@DisplayName("a percent nobody meant to type is refused at the edge")
	void rejectsAnAbsurdPercent() throws Exception {
		mvc.perform(post("/api/op/price-books/" + idOf(ACTIVE) + "/bulk-increase")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"target\":\"ALL\",\"percent\":1500}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser
	@DisplayName("a target the price book has no meaning for is refused")
	void rejectsAnUnknownTarget() throws Exception {
		mvc.perform(post("/api/op/price-books/" + idOf(ACTIVE) + "/bulk-increase")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"target\":\"EVERYTHING\",\"percent\":10}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser
	@DisplayName("raising a version that does not exist is a 404")
	void rejectsABulkIncreaseOnAnUnknownVersion() throws Exception {
		mvc.perform(post("/api/op/price-books/" + UUID.randomUUID() + "/bulk-increase")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"target\":\"ALL\",\"percent\":10}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser
	@DisplayName("GET /{id} shows a version's items and coefficients, with their units")
	void showsAVersionInFull() throws Exception {
		mvc.perform(get("/api/op/price-books/" + idOf(ACTIVE)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.versionCode").value(ACTIVE))
				.andExpect(jsonPath("$.editable")
						.value(false))
				.andExpect(jsonPath("$.items.length()").value(14))
				.andExpect(jsonPath("$.items[?(@.code=='WALL_PAINT')].unit")
						.value(org.hamcrest.Matchers.hasItem("SQM")))
				.andExpect(jsonPath("$.items[?(@.code=='MOBILIZATION')].unit")
						.value(org.hamcrest.Matchers.hasItem("LUMP_SUM")))
				.andExpect(jsonPath("$.coefficients.crewDayCost").value(7500.00))
				.andExpect(jsonPath("$.coefficients.marginRatio").value(0.3000));
	}

	@Test
	@WithMockUser
	@DisplayName("PUT corrects an item on a draft version")
	void correctsAnItemOnADraft() throws Exception {
		String created = mvc.perform(post("/api/op/price-books")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceId\":\"" + idOf(ACTIVE) + "\",\"versionCode\":\"WEB-DRAFT-1\"}"))
				.andReturn().getResponse().getContentAsString();
		String id = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		mvc.perform(put("/api/op/price-books/" + id + "/items/WALL_PAINT")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"materialCost\":40.00,\"labourMinutes\":7.00}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.materialCost").value(40.00))
				.andExpect(jsonPath("$.labourMinutes").value(7.00))
				// Derived, not sent: 7 minutes of a 7,500 TL crew day (ADR 0016). A request that carried a
				// labour cost of its own would have no way to say this.
				.andExpect(jsonPath("$.labourCost").value(36.46))
				// Read back from the row rather than echoed from the request.
				.andExpect(jsonPath("$.unit").value("SQM"));
	}

	@Test
	@WithMockUser
	@DisplayName("PUT on the live version is a conflict — copy it and edit the copy")
	void refusesToEditTheLiveVersion() throws Exception {
		mvc.perform(put("/api/op/price-books/" + idOf(ACTIVE) + "/items/WALL_PAINT")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"materialCost\":1.00,\"labourMinutes\":1.00}"))
				.andExpect(status().isConflict());
	}

	@Test
	@WithMockUser
	@DisplayName("an item that takes no time is refused: it would vanish from the duration silently")
	void refusesAnItemWithNoDuration() throws Exception {
		String created = mvc.perform(post("/api/op/price-books")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceId\":\"" + idOf(ACTIVE) + "\",\"versionCode\":\"WEB-DRAFT-2\"}"))
				.andReturn().getResponse().getContentAsString();
		String id = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		mvc.perform(put("/api/op/price-books/" + id + "/items/WALL_PAINT")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"materialCost\":40.00,\"labourMinutes\":0}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser
	@DisplayName("a version that does not exist has no detail")
	void detailOfAnUnknownVersionIsNotFound() throws Exception {
		mvc.perform(get("/api/op/price-books/" + UUID.randomUUID()))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser
	@DisplayName("a version code already in use is a conflict, not a server error")
	void rejectsADuplicateVersionCode() throws Exception {
		mvc.perform(post("/api/op/price-books")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceId\":\"" + idOf(ACTIVE) + "\",\"versionCode\":\"" + ACTIVE + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(ACTIVE)));
	}

	@Test
	@WithMockUser
	@DisplayName("cloning a version that does not exist is a 404")
	void rejectsAnUnknownSource() throws Exception {
		mvc.perform(post("/api/op/price-books")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceId\":\"" + UUID.randomUUID() + "\",\"versionCode\":\"WEB-ORPHAN\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser
	@DisplayName("a blank version code is refused before anything is copied")
	void rejectsABlankVersionCode() throws Exception {
		mvc.perform(post("/api/op/price-books")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sourceId\":\"" + idOf(ACTIVE) + "\",\"versionCode\":\"  \"}"))
				.andExpect(status().isBadRequest());
	}

	private UUID idOf(String versionCode) {
		return jdbc.queryForObject(
				"SELECT id FROM price_book WHERE version_code = ?", UUID.class, versionCode);
	}
}
