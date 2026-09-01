package com.burakpadr.decorating.architecture;

import com.burakpadr.decorating.quoting.domain.model.CloseOutcome;
import com.burakpadr.decorating.quoting.domain.model.ContactReason;
import com.burakpadr.decorating.quoting.domain.model.QuoteRequest;
import com.burakpadr.decorating.quoting.domain.model.QuoteStatus;
import java.util.UUID;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The four hard rules of §2, enforced. These are the rules that are expensive to retrofit, which
 * is exactly why they are checked by a test rather than by convention.
 *
 * <p>Every rule sets {@code allowEmptyShould(true)}: the module packages are still empty, and a
 * rule guarding a package that has no classes yet should pass, not fail.
 */
class ArchitectureRulesTest {

	private static JavaClasses classes;

	@BeforeAll
	static void importClasses() {
		classes = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.burakpadr.decorating");
	}

	@Test
	@DisplayName("Rule 1 — domain packages carry no framework annotations")
	void domainIsFrameworkFree() {
		ArchRule rule = noClasses()
				.that().resideInAPackage("..domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"org.springframework..",
						"jakarta.persistence..",
						"jakarta.validation..",
						"com.fasterxml.jackson..",
						// Spring Boot 4 moved to Jackson 3, which is a different root package. The rule
						// named only the old one, so a domain record could have carried an @JsonProperty
						// from tools.jackson and nothing would have said so.
						"tools.jackson..",
						"com.networknt..",
						"io.swagger..")
				.because("domain models are plain Java; JPA entities and DTOs are separate classes "
						+ "in the adapter layer, mapped explicitly")
				.allowEmptyShould(true);

		rule.check(classes);
	}

	@Test
	@DisplayName("Rule 2 — the domain never reaches into an adapter or into application code")
	void domainDependsOnNothingOutwards() {
		ArchRule rule = noClasses()
				.that().resideInAPackage("..quoting.domain..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"..quoting.adapter..",
						"..quoting.application..")
				.because("dependencies point inwards; the domain is reached through ports only")
				.allowEmptyShould(true);

		rule.check(classes);
	}

	/**
	 * Every module package, so adding a module means adding one line here and nothing else.
	 * A module's own name is filtered out when the rule for that module is built.
	 */
	private static final List<String> MODULES = List.of("quoting", "customer");

	@Test
	@DisplayName("Rule 3 — modules see each other only through published events")
	void modulesTouchEachOtherOnlyThroughEvents() {
		for (String module : MODULES) {
			String[] otherModulesExceptEvents = MODULES.stream()
					.filter(other -> !other.equals(module))
					.map(other -> "..decorating." + other + "..")
					.toArray(String[]::new);

			String[] otherModuleEvents = MODULES.stream()
					.filter(other -> !other.equals(module))
					.map(other -> "..decorating." + other + ".domain.event..")
					.toArray(String[]::new);

			ArchRule rule = noClasses()
					.that().resideInAPackage("..decorating." + module + "..")
					.should().dependOnClassesThat(
							JavaClass.Predicates.resideInAnyPackage(otherModulesExceptEvents)
									.and(JavaClass.Predicates.resideOutsideOfPackages(otherModuleEvents)))
					.because("events are the only integration seam (§2.4): " + module
							+ " may import another module's domain.event package and nothing else — "
							+ "scheduling must never reference quoting.domain.model.Quote")
					.allowEmptyShould(true);

			rule.check(classes);
		}
	}

	@Test
	@DisplayName("Rule 3c — an event may not drag its module across the boundary")
	void eventsExposeOnlySharedTypes() {
		ArchRule rule = noClasses()
				.that().resideInAPackage("..domain.event..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"..domain.model..",
						"..domain.service..",
						"..domain.port..",
						"..application..",
						"..adapter..")
				.because("an event is a module's public surface: it carries IDs and shared value "
						+ "objects only. Referencing quoting.domain.model.Quote from an event would "
						+ "let a subscriber reach the whole module and defeat the seam")
				.allowEmptyShould(true);

		rule.check(classes);
	}

	@Test
	@DisplayName("Rule 3b — shared depends on no module")
	void sharedDependsOnNoModule() {
		ArchRule rule = noClasses()
				.that().resideInAPackage("..decorating.shared..")
				.should().dependOnClassesThat().resideInAnyPackage(
						"..decorating.quoting..",
						"..decorating.customer..")
				.because("shared is the bottom of the dependency graph")
				.allowEmptyShould(true);

		rule.check(classes);
	}

	@Test
	@DisplayName("The pricing engine has zero dependencies beyond the domain and the JDK")
	void pricingEngineIsPure() {
		ArchRule rule = noClasses()
				.that().resideInAPackage("..quoting.domain.service..")
				.should().dependOnClassesThat().resideOutsideOfPackages(
						"java..",
						"..decorating.shared..",
						"..quoting.domain..")
				.because("PricingEngine must be unit-testable without a Spring context or a "
						+ "database — the most important testability requirement in the system")
				.allowEmptyShould(true);

		rule.check(classes);
	}

	@Test
	@DisplayName("Rule 4 — only the persistence adapter may rebuild a quote request from a status")
	void onlyPersistenceRehydratesAQuoteRequest() {
		// §3: "Do not let adapters set status directly." QuoteRequest has no status argument except
		// rehydrate(), which exists because a request outlives the process that made it. This is what
		// stops that door being used as a shortcut — an application service that rehydrates instead of
		// calling the event it means has just written a status by hand, with the state machine watching.
		ArchRule rule = noClasses()
				.that().resideOutsideOfPackages("..quoting.adapter.out.persistence..")
				.and().resideOutsideOfPackages("..quoting.domain.model..")
				.should().callMethod(QuoteRequest.class, "rehydrate", UUID.class, QuoteStatus.class,
						int.class, ContactReason.class, CloseOutcome.class)
				.because("a status set from outside the state machine is a request in a state nobody "
						+ "can explain, and the row keeps it forever")
				.allowEmptyShould(true);

		rule.check(classes);
	}
}
