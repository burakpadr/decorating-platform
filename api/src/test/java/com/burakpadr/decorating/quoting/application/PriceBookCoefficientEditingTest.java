package com.burakpadr.decorating.quoting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.burakpadr.decorating.TestcontainersConfiguration;
import com.burakpadr.decorating.quoting.domain.model.ActivationProblem;
import com.burakpadr.decorating.quoting.domain.model.ItemCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookCoefficients;
import com.burakpadr.decorating.quoting.domain.model.PriceBookDetail;
import com.burakpadr.decorating.quoting.domain.model.PriceBookNotActivatable;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionLocked;
import com.burakpadr.decorating.quoting.domain.port.in.ManagePriceBookVersions;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Editing a version's coefficients, and the gate in front of activation (BOYA-20a).
 *
 * <p>Two halves of one card. Until now the only figure an operator could change was an item's cost:
 * the day a painter's wage changed, the only move available was a percentage increase, and a ceiling
 * height of 2.90 instead of 2.70 could not be corrected from the panel at all. The other half is ADR
 * 0016's guard, which used to read whatever book was active in the test database — BOYA-72 took the
 * price book out of the migrations, so it now has to run where every book passes through it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PriceBookCoefficientEditingTest {

	private static final String ACTIVE = "REAL-2026-03";

	/** WALL_PAINT's six person-minutes at REAL-2026-03's 5,000 TL crew day over two people. */
	private static final String WALL_PAINT_LABOUR = "31.25";

	@Autowired
	private ManagePriceBookVersions versions;

	@Autowired
	private PriceBookRepository books;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void restoreTheActiveVersion() {
		jdbc.update("DELETE FROM price_book WHERE version_code LIKE 'TEST-%'");
		jdbc.update("UPDATE price_book SET active = false WHERE active = true");
		jdbc.update("UPDATE price_book SET active = true WHERE version_code = ?", ACTIVE);
	}

	// =============================================================================================
	// Editing the coefficients
	// =============================================================================================

	@Test
	@DisplayName("acceptance: raising the crew rate re-derives every item's labour, not just one")
	void raisingTheCrewRateRederivesLabour() {
		PriceBookSummary copy = versions.createVersionFrom(idOf(ACTIVE), "TEST-COEF-1");

		versions.updateCoefficients(copy.id(), coefficients(c -> c.crewDayCost = new BigDecimal("10000.00")));

		PriceBook edited = books.findById(copy.id()).orElseThrow();
		assertThat(edited.crewDayCost()).isEqualByComparingTo("10000.00");
		assertThat(edited.item(ItemCode.WALL_PAINT).labourCost())
				.as("twice the crew rate is twice the labour, and it is derived rather than copied")
				.isEqualByComparingTo("62.50");
		assertThat(edited.item(ItemCode.WALL_PAINT).labourMinutes())
				.as("the duration is untouched: the crew got dearer, the work did not get slower")
				.isEqualByComparingTo("6");
		assertThat(new com.burakpadr.decorating.quoting.domain.service.ActivationCheck().check(edited))
				.as("and the version it leaves behind is still fit to activate")
				.isEmpty();
	}

	@Test
	@DisplayName("acceptance: the source version is not touched — a new one carries the change")
	void theSourceVersionIsUntouched() {
		PriceBookSummary copy = versions.createVersionFrom(idOf(ACTIVE), "TEST-COEF-2");

		versions.updateCoefficients(copy.id(), coefficients(c -> c.ceilingHeight = new BigDecimal("2.90")));

		assertThat(books.findByVersionCode(ACTIVE).orElseThrow().ceilingHeightM())
				.as("the live list still prices at the height it was quoting with")
				.isEqualByComparingTo("2.70");
		assertThat(books.findById(copy.id()).orElseThrow().ceilingHeightM()).isEqualByComparingTo("2.90");
	}

	@Test
	@DisplayName("the live version cannot be edited: copy it and edit the copy")
	void theLiveVersionIsLocked() {
		UUID live = idOf(ACTIVE);

		assertThatThrownBy(() -> versions.updateCoefficients(live, coefficients(c -> { })))
				.as("§4.5's promise: a figure a customer was shown stays explainable afterwards")
				.isInstanceOf(PriceBookVersionLocked.class);
	}

	@Test
	@DisplayName("the answer carries the whole version back, so the panel can show what changed")
	void theAnswerCarriesTheEditedVersion() {
		PriceBookSummary copy = versions.createVersionFrom(idOf(ACTIVE), "TEST-COEF-3");

		PriceBookDetail detail =
				versions.updateCoefficients(copy.id(), coefficients(c -> c.margin = new BigDecimal("0.3500")));

		assertThat(detail.book().marginRatio()).isEqualByComparingTo("0.3500");
		assertThat(detail.editable()).isTrue();
	}

	// =============================================================================================
	// The gate in front of activation
	// =============================================================================================

	@Test
	@DisplayName("acceptance: a version whose labour disagrees with its crew rate cannot go live")
	void activationRefusesAnUnreconciledVersion() {
		PriceBookSummary copy = versions.createVersionFrom(idOf(ACTIVE), "TEST-COEF-4");
		// Written straight to the database, which is the only way to produce this row now: every path
		// through the use case derives labour instead of accepting it. That is the point — the guard is
		// for the ones that come from somewhere else, including the setup wizard (BOYA-70).
		jdbc.update("UPDATE price_book_item SET labour_cost = 99.00 "
				+ "WHERE price_book_id = ? AND code = 'WALL_PAINT'", copy.id());

		assertThatThrownBy(() -> versions.activate(copy.id()))
				.isInstanceOf(PriceBookNotActivatable.class)
				.satisfies(thrown -> {
					PriceBookNotActivatable refused = (PriceBookNotActivatable) thrown;
					assertThat(refused.problems()).singleElement().satisfies(problem -> {
						assertThat(problem.kind())
								.isEqualTo(ActivationProblem.Kind.LABOUR_DOES_NOT_RECONCILE);
						assertThat(problem.subject()).isEqualTo("WALL_PAINT");
					});
				});

		assertThat(books.findActive().orElseThrow().versionCode())
				.as("and the refusal changes nothing: the live list is where it was")
				.isEqualTo(ACTIVE);
	}

	@Test
	@DisplayName("a version the panel produced passes the gate untouched")
	void anEditedVersionActivatesCleanly() {
		PriceBookSummary copy = versions.createVersionFrom(idOf(ACTIVE), "TEST-COEF-5");
		versions.updateCoefficients(copy.id(), coefficients(c -> {
			c.crewSize = 3;
			c.crewDayCost = new BigDecimal("7500.00");
		}));

		versions.activate(copy.id());

		assertThat(books.findActive().orElseThrow().versionCode()).isEqualTo("TEST-COEF-5");
		assertThat(books.findActive().orElseThrow().item(ItemCode.WALL_PAINT).labourCost())
				.as("three people at 7,500 TL cost the same per person-minute as two at 5,000")
				.isEqualByComparingTo(WALL_PAINT_LABOUR);
	}

	// =============================================================================================
	// Over HTTP
	// =============================================================================================

	@Test
	@WithMockUser
	@DisplayName("the panel edits coefficients over one call and gets the version back")
	void editsOverHttp() throws Exception {
		PriceBookSummary copy = versions.createVersionFrom(idOf(ACTIVE), "TEST-COEF-6");

		mvc.perform(put("/api/op/price-books/{id}/coefficients", copy.id())
						.contentType(MediaType.APPLICATION_JSON).content(body("2.90", "5000.00", "0.3000")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.coefficients.ceilingHeightM").value(2.90))
				.andExpect(jsonPath("$.editable").value(true))
				.andExpect(jsonPath("$.items[?(@.code=='WALL_PAINT')].labourCost")
						.value(org.hamcrest.Matchers.hasItem(31.25)));
	}

	@Test
	@WithMockUser
	@DisplayName("a coefficient outside its bounds is refused with the reason, in Turkish")
	void refusesAnImpossibleCoefficientOverHttp() throws Exception {
		PriceBookSummary copy = versions.createVersionFrom(idOf(ACTIVE), "TEST-COEF-7");

		mvc.perform(put("/api/op/price-books/{id}/coefficients", copy.id())
						.contentType(MediaType.APPLICATION_JSON).content(body("27.00", "5000.00", "0.3000")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("tavan")));
	}

	@Test
	@WithMockUser
	@DisplayName("a refused activation answers 409 and names what is wrong with the version")
	void refusedActivationOverHttp() throws Exception {
		PriceBookSummary copy = versions.createVersionFrom(idOf(ACTIVE), "TEST-COEF-8");
		jdbc.update("DELETE FROM price_book_item WHERE price_book_id = ? AND code = 'MOBILIZATION'",
				copy.id());

		mvc.perform(post("/api/op/price-books/{id}/activate", copy.id()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value("urn:decorating:price-book-not-activatable"))
				.andExpect(jsonPath("$.problems[0].kind").value("MISSING_ITEM"))
				.andExpect(jsonPath("$.problems[0].subject").value("MOBILIZATION"));
	}

	// =============================================================================================

	private static String body(String ceilingHeight, String crewDayCost, String margin) {
		return """
				{"ceilingHeightM":%s,"grossToNetRatio":0.8200,"stage1OpeningRatio":0.1200,
				 "crewSize":2,"crewHoursPerDay":8.00,"crewDayCost":%s,
				 "marginRatio":%s,"marginAlertThreshold":0.2000,
				 "labourVatRate":0.2000,"materialVatRate":0.1000}
				""".formatted(ceilingHeight, crewDayCost, margin);
	}

	/** REAL-2026-03's own coefficients, with whatever the test wants to move. */
	private static PriceBookCoefficients coefficients(java.util.function.Consumer<Draft> change) {
		Draft draft = new Draft();
		change.accept(draft);
		return new PriceBookCoefficients(draft.ceilingHeight, new BigDecimal("0.8200"),
				new BigDecimal("0.1200"), draft.crewSize, new BigDecimal("8.00"), draft.crewDayCost,
				draft.margin, new BigDecimal("0.2000"), new BigDecimal("0.2000"), new BigDecimal("0.1000"));
	}

	private static final class Draft {
		private BigDecimal ceilingHeight = new BigDecimal("2.70");
		private int crewSize = 2;
		private BigDecimal crewDayCost = new BigDecimal("5000.00");
		private BigDecimal margin = new BigDecimal("0.3000");
	}

	private UUID idOf(String versionCode) {
		return jdbc.queryForObject("SELECT id FROM price_book WHERE version_code = ?", UUID.class,
				versionCode);
	}
}
