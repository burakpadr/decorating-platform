package com.burakpadr.decorating.quoting.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The district list (§7, BOYA-26).
 *
 * <p>It is the first question of stage 1 and therefore the first thing anybody sees, which is why it
 * comes from the active price book rather than from a constant: turning a district off is how the
 * business closes an area, and a list compiled at build time would keep sending it work.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DistrictControllerTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void removeTheDecoy() {
		jdbc.update("DELETE FROM service_district WHERE district_code = 'TEST_CLOSED'");
	}

	@Test
	@DisplayName("the districts we serve, anonymously — it is the question before the session exists")
	void listsTheServedDistricts() throws Exception {
		mvc.perform(get("/api/districts"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(39))
				.andExpect(jsonPath("$[0].code").value("ADALAR"))
				.andExpect(jsonPath("$[0].name").value("Adalar"));
	}

	@Test
	@DisplayName("acceptance: a district that has been switched off does not appear")
	void hidesAnInactiveDistrict() throws Exception {
		jdbc.update("""
				INSERT INTO service_district (id, price_book_id, district_code, display_name, active,
				  district_factor)
				SELECT ?, id, 'TEST_CLOSED', 'Kapalı İlçe', false, 1.0000
				FROM price_book WHERE active = true
				""", UUID.randomUUID());

		mvc.perform(get("/api/districts"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("TEST_CLOSED"))))
				.andExpect(jsonPath("$.length()").value(39));
	}

	@Test
	@DisplayName("§1: the list carries no district factor — a customer is not told Kadıköy costs more")
	void doesNotExposeThePricingFactor() throws Exception {
		String body = mvc.perform(get("/api/districts")).andReturn().getResponse()
				.getContentAsString();

		org.assertj.core.api.Assertions.assertThat(body)
				.as("the factor is what the business charges for an area, and it belongs with the cost")
				.doesNotContain("factor").doesNotContain("1.0");
	}

	@Test
	@DisplayName("the list is alphabetical, because a customer scans it rather than searching it")
	void isOrdered() throws Exception {
		mvc.perform(get("/api/districts"))
				.andExpect(jsonPath("$[0].name").value("Adalar"))
				.andExpect(jsonPath("$[38].name").value("Zeytinburnu"));
	}
}
