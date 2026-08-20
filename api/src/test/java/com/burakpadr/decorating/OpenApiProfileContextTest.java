package com.burakpadr.decorating;

import static org.assertj.core.api.Assertions.assertThat;

import com.burakpadr.decorating.quoting.domain.port.out.PriceBookRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * The {@code openapi} profile has to boot the whole application with no database behind it: the
 * contract is exported by starting it, fetching {@code /v3/api-docs} and stopping it, and CI
 * regenerates the file on every run.
 *
 * <p>This test exists because of a specific way it breaks. A controller needs a use case, a use case
 * needs an adapter, and an adapter needs a repository — so the profile cannot simply exclude the
 * datasource. It configures one that never connects instead, and if that ever stops working the
 * failure appears as a Maven plugin unable to reach a JMX port, which reads like a build problem
 * rather than a boot failure.
 *
 * <p><b>No Docker and no database.</b> That is the assertion: this test runs in the ordinary suite
 * with nothing started, so if the graph ever needs a real connection to come up, it fails here rather
 * than in the release pipeline.
 */
@SpringBootTest
@ActiveProfiles("openapi")
class OpenApiProfileContextTest {

	@Autowired
	private ApplicationContext context;

	@Test
	@DisplayName("the whole graph boots for the contract export, without connecting to anything")
	void bootsTheFullGraphWithoutADatabase() {
		assertThat(context.getBeanNamesForType(PriceBookRepository.class))
				.as("the export describes the API the adapters serve, so the graph has to be complete")
				.hasSize(1);
		assertThat(context.containsBean("flyway"))
				.as("migrations must not run against a database this profile never reaches")
				.isFalse();
	}
}
