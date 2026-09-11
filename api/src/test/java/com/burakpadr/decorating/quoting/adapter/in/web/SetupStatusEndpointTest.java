package com.burakpadr.decorating.quoting.adapter.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What {@code GET /api/op/setup/status} answers once the install has a price book (BOYA-69).
 *
 * <p>The empty answer is {@code UninstalledSystemTest}'s, which needs a context with no price book.
 * Here the question is the other one, and it is the one that regresses quietly: a check that reports
 * something missing on a working install would send the operator into the setup wizard every time
 * they opened the panel.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SetupStatusEndpointTest {

	@Autowired
	private MockMvc mvc;

	@Test
	@DisplayName("setup status is not readable without an operator login")
	void refusesAnonymousRequests() throws Exception {
		// It says what an install has not configured. That is not a visitor's business, and the panel is
		// behind the operator realm anyway (§7).
		mvc.perform(get("/api/op/setup/status")).andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	@DisplayName("an install with an active price book is set up, and says so with an empty list")
	void theSeededInstallIsComplete() throws Exception {
		mvc.perform(get("/api/op/setup/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.complete").value(true))
				.andExpect(jsonPath("$.missing.length()").value(0));
	}
}
