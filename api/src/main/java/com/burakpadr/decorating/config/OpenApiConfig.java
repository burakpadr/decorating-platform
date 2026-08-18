package com.burakpadr.decorating.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The spec served here is the source of the generated TypeScript client. The
 * Maven {@code openapi} profile writes it to {@code api-client/openapi.json} and CI
 * fails on a diff, so a DTO change that breaks the frontend surfaces in the same pull request.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI decoratingOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Decorating Platform API")
						.version("v1")
						.description("Quoting and customer modules."))
				// Pinned. Left to springdoc this would carry the port the spec happened to be
				// generated on, and the committed file would churn on every run.
				.servers(List.of(new Server().url("/").description("Same origin")));
	}
}
