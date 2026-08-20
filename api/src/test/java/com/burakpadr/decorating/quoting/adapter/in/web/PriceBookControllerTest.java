package com.burakpadr.decorating.quoting.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
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

	private static final String ACTIVE = "REAL-2026-01";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	/** The database is shared with every other test in the suite; leave it as it was found. */
	@AfterEach
	void restoreTheActiveVersion() {
		jdbc.update("DELETE FROM price_book WHERE version_code LIKE 'WEB-%'");
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
