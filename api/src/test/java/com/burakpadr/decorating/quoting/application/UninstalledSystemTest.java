package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.config.session.AnonymousSessionCookie;
import com.burakpadr.decorating.quoting.domain.model.AreaBasis;
import com.burakpadr.decorating.quoting.domain.model.Furnishing;
import com.burakpadr.decorating.quoting.domain.model.Layout;
import com.burakpadr.decorating.quoting.domain.model.MissingSetting;
import com.burakpadr.decorating.quoting.domain.model.QuoteCalculationCommand;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteScope;
import com.burakpadr.decorating.quoting.domain.model.SetupIncomplete;
import com.burakpadr.decorating.quoting.domain.model.StageOneAnswers;
import com.burakpadr.decorating.quoting.domain.model.WallCondition;
import com.burakpadr.decorating.quoting.domain.port.in.CalculateQuote;
import com.burakpadr.decorating.quoting.domain.port.in.EstimateStageOne;
import com.burakpadr.decorating.quoting.domain.port.in.GenerateQuote;
import com.burakpadr.decorating.quoting.domain.port.in.ReadSetupStatus;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import com.burakpadr.decorating.quoting.domain.port.out.QuoteRequestRepository;
import com.burakpadr.decorating.shared.Uuid7;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A system nobody has set up does not quote anybody (BOYA-69).
 *
 * <p>The repository is mocked empty rather than the fixture being deleted: the shared database is
 * where every other test's price book lives, and a test that switched off the active version would
 * be asserting about the other tests' ordering rather than about an empty install.
 *
 * <p>The acceptance criterion is the shape of the refusal, not just its existence. The customer end
 * has to be recognisable by a machine — a client that has to match a Turkish sentence to tell "not
 * set up" from "something broke" will show the wrong screen the first time the wording changes
 * (BOYA-27's arrangement, workflow §8).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UninstalledSystemTest {

	private static final String A_JOB = """
			{"districtCode":"KADIKOY","area":92,"areaBasis":"NET","layout":"THREE_PLUS_ONE",
			 "scope":"WHOLE_HOME","wallCondition":"MINOR","furnishing":"FURNISHED","doorCount":8,
			 "doorColourChange":true,"hasElevator":true}
			""";

	@MockitoBean
	private PriceBookRepository priceBooks;

	@Autowired
	private CalculateQuote calculator;

	@Autowired
	private EstimateStageOne estimates;

	@Autowired
	private GenerateQuote quotes;

	@Autowired
	private ReadSetupStatus setup;

	@Autowired
	private QuoteRequestRepository requests;

	@Autowired
	private AnonymousSessionCookie session;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void nothingIsInstalled() {
		given(priceBooks.findActive()).willReturn(Optional.empty());
		given(priceBooks.findById(org.mockito.ArgumentMatchers.any())).willReturn(Optional.empty());
	}

	@AfterEach
	void removeWhatTheTestWrote() {
		jdbc.update("DELETE FROM quote_request WHERE customer_id IS NULL");
	}

	@Test
	@DisplayName("the operator's own tool cannot price a job either")
	void calculateQuoteRefuses() {
		assertThatThrownBy(() -> calculator.calculate(new QuoteCalculationCommand(
						"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
						QuoteScope.WHOLE_HOME, Set.of(), WallCondition.MINOR, Furnishing.FURNISHED,
						8, true, false, true, false)))
				.isInstanceOf(SetupIncomplete.class);
	}

	@Test
	@DisplayName("acceptance: stage 1 refuses rather than answering a range")
	void estimateStageOneRefuses() {
		assertThatThrownBy(() -> estimates.estimate(answered()))
				.as("a range from an empty install is a number the customer will hold us to")
				.isInstanceOf(SetupIncomplete.class);
	}

	@Test
	@DisplayName("acceptance: stage 2 refuses rather than generating a quote")
	void generateQuoteRefuses() {
		assertThatThrownBy(() -> quotes.generate(answered()))
				.isInstanceOf(SetupIncomplete.class);
	}

	@Test
	@DisplayName("acceptance: the customer end refuses with a type a client can recognise")
	void theCustomerEndSaysSoInAWayAMachineCanRead() throws Exception {
		UUID id = answered();

		mvc.perform(post("/api/quote-requests/{id}/estimate", id)
						.cookie(new Cookie(AnonymousSessionCookie.NAME, session.mint(id))))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value("urn:decorating:not-set-up"))
				// What is missing is the operator's business. A visitor learning which coefficients this
				// install has not entered learns about its insides, and can do nothing with it.
				.andExpect(jsonPath("$.missing").doesNotExist());
	}

	@Test
	@WithMockUser
	@DisplayName("the panel gets the same refusal, with what to ask for")
	void thePanelIsToldWhichSettingsAreMissing() throws Exception {
		mvc.perform(post("/api/op/price-calculations")
						.contentType(MediaType.APPLICATION_JSON).content(A_JOB))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.type").value("urn:decorating:not-set-up"))
				.andExpect(jsonPath("$.missing")
						.value(org.hamcrest.Matchers.hasItem(MissingSetting.PRICE_BOOK.name())));
	}

	@Test
	@WithMockUser
	@DisplayName("acceptance: the status endpoint names what setup still owes")
	void theStatusEndpointNamesWhatIsMissing() throws Exception {
		mvc.perform(get("/api/op/setup/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.complete").value(false))
				.andExpect(jsonPath("$.missing.length()").value(MissingSetting.values().length))
				.andExpect(jsonPath("$.missing")
						.value(org.hamcrest.Matchers.hasItem(MissingSetting.PRICE_BOOK.name())));

		assertThat(setup.status().complete()).isFalse();
	}

	private UUID answered() {
		QuoteRequest draft = QuoteRequest.draft(Uuid7.generate()).answer(new StageOneAnswers(
				"KADIKOY", new BigDecimal("92"), AreaBasis.NET, Layout.THREE_PLUS_ONE,
				QuoteScope.WHOLE_HOME, Furnishing.FURNISHED, 8, true, WallCondition.MINOR, null));
		requests.save(draft);
		return draft.id();
	}
}
