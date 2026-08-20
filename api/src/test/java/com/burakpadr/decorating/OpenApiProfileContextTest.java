package com.burakpadr.decorating;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * The {@code openapi} profile has to boot with no infrastructure at all: the contract is exported by
 * starting the application, fetching {@code /v3/api-docs} and stopping it, and CI regenerates the file
 * on every run.
 *
 * <p>This test exists because of a specific way it breaks. The profile excludes the datasource, JPA
 * and Flyway, so any bean that needs a repository cannot be created — and the failure appears as a
 * Maven plugin unable to reach a JMX port, which reads like a build problem rather than a missing
 * {@code @Profile("!openapi")} on an adapter written an hour earlier.
 *
 * <p>No Docker and no database: that is the point of the profile and therefore of this test.
 */
@SpringBootTest
@ActiveProfiles("openapi")
class OpenApiProfileContextTest {

	@Autowired
	private ApplicationContext context;

	@Test
	@DisplayName("the context boots for the contract export, with no datasource behind it")
	void bootsWithoutInfrastructure() {
		assertThat(context.getBeanNamesForType(DataSource.class))
				.as("the profile exists to export the contract without infrastructure")
				.isEmpty();
	}
}
