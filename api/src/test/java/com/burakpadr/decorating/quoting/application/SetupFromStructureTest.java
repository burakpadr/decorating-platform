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
import com.burakpadr.decorating.quoting.domain.model.MissingSetting;
import com.burakpadr.decorating.quoting.domain.model.ModifierCode;
import com.burakpadr.decorating.quoting.domain.model.PriceBook;
import com.burakpadr.decorating.quoting.domain.model.PriceBookCoefficients;
import com.burakpadr.decorating.quoting.domain.model.PriceBookNotActivatable;
import com.burakpadr.decorating.quoting.domain.model.PriceBookSummary;
import com.burakpadr.decorating.quoting.domain.model.PriceBookVersionLocked;
import com.burakpadr.decorating.quoting.domain.model.RoomType;
import com.burakpadr.decorating.quoting.domain.model.ServiceDistrict;
import com.burakpadr.decorating.quoting.domain.port.in.ManagePriceBookVersions;
import com.burakpadr.decorating.quoting.domain.port.in.ReadSetupStatus;
import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import java.math.BigDecimal;
import java.util.List;
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
 * The two calls the setup wizard needs before it can exist (BOYA-70's backend).
 *
 * <p>A fresh install has no price book at all (BOYA-72), and the only way to make one was to copy an
 * existing version — so there was nothing to copy and no way to start. It also had no way to enter a
 * district: the sole INSERT into {@code service_district} lived inside that copy.
 *
 * <p>So: build a version from the shipped structure, and set a version's districts. Everything else
 * the wizard does already exists — coefficients and item costs from BOYA-20a, activation with its
 * gate, and {@code setup/status} from BOYA-69 to tell it what is still missing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SetupFromStructureTest {

	private static final String ACTIVE = "REAL-2026-03";

	@Autowired
	private ManagePriceBookVersions versions;

	@Autowired
	private PriceBookRepository books;

	@Autowired
	private ReadSetupStatus setup;

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
	// A version out of the shipped structure
	// =============================================================================================

	@Test
	@DisplayName("acceptance: a version from the structure has every code the engine looks up")
	void theStructureProducesACompleteVersion() {
		PriceBookSummary created = versions.createFromStructure("TEST-SETUP-1");

		PriceBook book = books.findById(created.id()).orElseThrow();
		assertThat(book.items().keySet()).containsExactlyInAnyOrder(ItemCode.values());
		assertThat(book.roomTypes().keySet()).containsExactlyInAnyOrder(RoomType.values());
		assertThat(book.modifiers().keySet()).containsExactlyInAnyOrder(ModifierCode.values());
		assertThat(book.item(ItemCode.WALL_PAINT).labourMinutes())
				.as("durations ship with the structure; §5.11's estimate until BOYA-1 corrects it")
				.isEqualByComparingTo("6.00");
		assertThat(book.roomTypes().get(RoomType.HALLWAY).perimeterFactor())
				.isEqualByComparingTo("5.50");
	}

	@Test
	@DisplayName("acceptance: it arrives with no money in it, and inactive")
	void theVersionArrivesEmptyOfMoney() {
		PriceBookSummary created = versions.createFromStructure("TEST-SETUP-2");

		PriceBook book = books.findById(created.id()).orElseThrow();
		assertThat(created.active()).isFalse();
		assertThat(book.crewDayCost()).isEqualByComparingTo("0.00");
		assertThat(book.marginRatio()).isEqualByComparingTo("0.0000");
		assertThat(book.labourVatRate()).isEqualByComparingTo("0.0000");
		assertThat(book.materialVatRate()).isEqualByComparingTo("0.0000");
		assertThat(book.averageJobValue())
				.as("the schema still defaults this to 25,000 TL — a price from a migration, which "
						+ "decision 0024 says a fresh install must not have")
				.isEqualByComparingTo("0.00");
		assertThat(book.items().values())
				.allSatisfy(item -> assertThat(item.materialCost()).isEqualByComparingTo("0.00"));
		assertThat(book.districts())
				.as("the installing business enters its own; ours are not somebody else's service area")
				.isEmpty();
	}

	@Test
	@DisplayName("acceptance: that version cannot go live until the figures are entered")
	void anEmptyVersionCannotBeActivated() {
		PriceBookSummary created = versions.createFromStructure("TEST-SETUP-3");

		assertThatThrownBy(() -> versions.activate(created.id()))
				.isInstanceOf(PriceBookNotActivatable.class)
				.satisfies(thrown -> assertThat(((PriceBookNotActivatable) thrown).problems())
						.extracting(ActivationProblem::subject)
						.contains("crewDayCost", "marginRatio", "labourVatRate", "materialVatRate"));
	}

	@Test
	@DisplayName("acceptance: the whole wizard, end to end, without a line of SQL")
	void theWholeSetupSequence() {
		// Exactly what the screens will do, in order: build the shape, enter the money, enter the
		// material costs, enter the districts, go live.
		PriceBookSummary created = versions.createFromStructure("TEST-SETUP-4");
		versions.updateCoefficients(created.id(), new PriceBookCoefficients(
				new BigDecimal("2.70"), new BigDecimal("0.8200"), new BigDecimal("0.1200"),
				2, new BigDecimal("8.00"), new BigDecimal("5000.00"),
				new BigDecimal("0.3000"), new BigDecimal("0.2000"),
				new BigDecimal("0.2000"), new BigDecimal("0.1000")));
		versions.updateItem(created.id(), ItemCode.WALL_PAINT, new BigDecimal("22.00"),
				new BigDecimal("6.00"));
		versions.replaceDistricts(created.id(), List.of(
				new ServiceDistrict("KADIKOY", "Kadıköy", true, new BigDecimal("1.0500")),
				new ServiceDistrict("USKUDAR", "Üsküdar", true, new BigDecimal("1.0000"))));

		versions.activate(created.id());

		PriceBook live = books.findActive().orElseThrow();
		assertThat(live.versionCode()).isEqualTo("TEST-SETUP-4");
		assertThat(live.item(ItemCode.WALL_PAINT).labourCost())
				.as("labour followed the crew rate the wizard entered, without being asked for")
				.isEqualByComparingTo("31.25");
		assertThat(live.item(ItemCode.WALL_PAINT).materialCost()).isEqualByComparingTo("22.00");
		assertThat(live.serves("KADIKOY")).isTrue();
		assertThat(setup.status().missing())
				.as("and BOYA-69 now reports the install as set up")
				.isEmpty();
	}

	// =============================================================================================
	// Districts
	// =============================================================================================

	@Test
	@DisplayName("the district list is replaced whole, and the display name is kept as typed")
	void districtsAreReplacedWhole() {
		PriceBookSummary created = versions.createFromStructure("TEST-SETUP-5");
		versions.replaceDistricts(created.id(), List.of(
				new ServiceDistrict("KADIKOY", "Kadıköy", true, new BigDecimal("1.0500")),
				new ServiceDistrict("SISLI", "Şişli", true, new BigDecimal("1.1000"))));

		versions.replaceDistricts(created.id(), List.of(
				new ServiceDistrict("SISLI", "Şişli", false, new BigDecimal("1.1000"))));

		PriceBook book = books.findById(created.id()).orElseThrow();
		assertThat(book.districts().keySet()).containsExactly("SISLI");
		assertThat(book.districts().get("SISLI").displayName()).isEqualTo("Şişli");
		assertThat(book.serves("SISLI"))
				.as("switched off is a district we keep and do not serve (§4.5)")
				.isFalse();
	}

	@Test
	@DisplayName("districts cannot be changed on a version that has priced something")
	void districtsAreLockedOnTheLiveVersion() {
		UUID live = idOf(ACTIVE);

		assertThatThrownBy(() -> versions.replaceDistricts(live, List.of(
						new ServiceDistrict("KADIKOY", "Kadıköy", true, BigDecimal.ONE))))
				.as("closing a district is a new version, like every other change to a price book")
				.isInstanceOf(PriceBookVersionLocked.class);
	}

	@Test
	@DisplayName("a district list that repeats a code is refused before anything is written")
	void duplicateDistrictCodesAreRefused() {
		PriceBookSummary created = versions.createFromStructure("TEST-SETUP-6");

		assertThatThrownBy(() -> versions.replaceDistricts(created.id(), List.of(
						new ServiceDistrict("KADIKOY", "Kadıköy", true, BigDecimal.ONE),
						new ServiceDistrict("KADIKOY", "Kadıköy (2)", true, BigDecimal.ONE))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("KADIKOY");

		assertThat(books.findById(created.id()).orElseThrow().districts())
				.as("refused before the write, not halfway through it")
				.isEmpty();
	}

	@Test
	@DisplayName("a district factor of 12 is a mistyped 1.2, and 0 prices the area at nothing")
	void districtFactorsAreBounded() {
		PriceBookSummary created = versions.createFromStructure("TEST-SETUP-7");

		assertThatThrownBy(() -> versions.replaceDistricts(created.id(), List.of(
						new ServiceDistrict("KADIKOY", "Kadıköy", true, new BigDecimal("12.0000")))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> versions.replaceDistricts(created.id(), List.of(
						new ServiceDistrict("KADIKOY", "Kadıköy", true, BigDecimal.ZERO))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> versions.replaceDistricts(created.id(), List.of(
						new ServiceDistrict("KADIKOY", "   ", true, BigDecimal.ONE))))
				.as("the display name is what the customer picks from a list")
				.isInstanceOf(IllegalArgumentException.class);
	}

	// =============================================================================================
	// Over HTTP
	// =============================================================================================

	@Test
	@WithMockUser
	@DisplayName("the panel builds the version and sets its districts over two calls")
	void theTwoCallsOverHttp() throws Exception {
		String id = mvc.perform(post("/api/op/price-books/from-structure")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"versionCode\":\"TEST-SETUP-8\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.active").value(false))
				.andReturn().getResponse().getContentAsString()
				.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

		mvc.perform(put("/api/op/price-books/{id}/districts", id)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"districts":[
								  {"code":"KADIKOY","displayName":"Kadıköy","active":true,"factor":1.0500}
								]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.districts.length()").value(1))
				.andExpect(jsonPath("$.districts[0].displayName").value("Kadıköy"));
	}

	@Test
	@WithMockUser
	@DisplayName("a version code that is taken is a conflict, not a second version")
	void aTakenCodeIsRefusedOverHttp() throws Exception {
		mvc.perform(post("/api/op/price-books/from-structure")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"versionCode\":\"" + ACTIVE + "\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	@WithMockUser
	@DisplayName("setup status says what is left while the wizard is halfway through")
	void statusReportsProgress() throws Exception {
		mvc.perform(post("/api/op/price-books/from-structure")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"versionCode\":\"TEST-SETUP-9\"}"))
				.andExpect(status().isCreated());

		// Still incomplete, and for the reason that matters: the new version is not live yet, so the
		// install is still being answered by the one that is.
		assertThat(setup.status().missing()).doesNotContain(MissingSetting.PRICE_BOOK);
	}

	private UUID idOf(String versionCode) {
		return jdbc.queryForObject("SELECT id FROM price_book WHERE version_code = ?", UUID.class,
				versionCode);
	}
}
