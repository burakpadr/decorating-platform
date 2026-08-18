# Maven owns api/; pnpm owns web-ui/ and api-client/. This file is the seam between them.

.DEFAULT_GOAL := help
.PHONY: help install infra infra-down dev dev-api dev-web build test test-api test-web watch-web test-one client clean

COMPOSE_DEV := docker compose -f infra/docker-compose.dev.yml
MVN         := cd api && ./mvnw -B

help: ## List targets
	@grep -hE '^[a-z-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

install: ## Install workspace dependencies
	pnpm install

infra: ## Start Postgres and MinIO for local development
	$(COMPOSE_DEV) up -d
	@echo "postgres :5432   minio :9000   console http://localhost:9001 (minioadmin/minioadmin)"

infra-down: ## Stop local infrastructure (volumes survive)
	$(COMPOSE_DEV) down

dev: infra ## Start the API and the web app against local infrastructure
	@echo "Run 'make dev-api' and 'make dev-web' in separate terminals."

dev-api: ## Run the API with hot reload
	$(MVN) spring-boot:run

dev-web: ## Run the web app with hot reload
	pnpm --filter @decorating/web dev

build: client ## Build everything
	$(MVN) package -DskipTests
	pnpm --filter @decorating/web build

test: test-api test-web ## Run every test suite

test-api: ## Run the API test suite (needs Docker for Testcontainers)
	$(MVN) test

test-web: ## Run the frontend test suites and typecheck
	pnpm -r test
	pnpm -r typecheck

watch-web: ## Frontend tests in watch mode — the red/green loop
	pnpm --filter @decorating/web test:watch

test-one: ## Run one backend test class or method: make test-one TEST=PricingEngineTest#appliesFurnishedToLabourOnly
	@test -n "$(TEST)" || { echo "usage: make test-one TEST=ClassName[#method]"; exit 1; }
	$(MVN) test -Dtest='$(TEST)'

client: ## Regenerate openapi.json and the TypeScript client, then commit the result
	$(MVN) -Popenapi verify -DskipTests
	pnpm --filter @decorating/api-client generate

clean: ## Remove build output
	$(MVN) clean
	rm -rf web-ui/.output web-ui/.nuxt
