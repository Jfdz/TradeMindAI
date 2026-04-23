.PHONY: help up down infra-up infra-down logs test build clean

DOCKER_COMPOSE = docker compose
SERVICES = market-data-service trading-core-service ai-engine web-app

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}'

# ── Infrastructure ────────────────────────────────────────────────────────────

infra-up: ## Start infrastructure only (PostgreSQL, Redis, RabbitMQ)
	$(DOCKER_COMPOSE) up -d postgres redis rabbitmq
	@echo "Waiting for PostgreSQL to be ready..."
	@until docker compose exec postgres pg_isready -U trading_user -d trading_saas; do sleep 1; done
	@echo "Infrastructure is ready."

infra-down: ## Stop infrastructure containers
	$(DOCKER_COMPOSE) stop postgres redis rabbitmq

# ── Services ──────────────────────────────────────────────────────────────────

up: ## Start all services + infrastructure
	$(DOCKER_COMPOSE) up -d

down: ## Stop all containers
	$(DOCKER_COMPOSE) down

restart: ## Restart all services (keep volumes)
	$(DOCKER_COMPOSE) restart

logs: ## Tail logs for all services
	$(DOCKER_COMPOSE) logs -f

logs-%: ## Tail logs for a specific service (e.g. make logs-market-data-service)
	$(DOCKER_COMPOSE) logs -f $*

# ── Build ─────────────────────────────────────────────────────────────────────

build: ## Build all Docker images
	$(DOCKER_COMPOSE) build --no-cache

build-%: ## Build a specific service image (e.g. make build-market-data-service)
	$(DOCKER_COMPOSE) build --no-cache $*

# ── Testing ───────────────────────────────────────────────────────────────────

test: ## Run all service tests
	@echo "=== market-data-service tests ==="
	cd services/market-data-service && mvn test -q
	@echo "=== trading-core-service tests ==="
	cd services/trading-core-service && mvn test -q
	@echo "=== ai-engine tests ==="
	cd services/ai-engine && python -m pytest tests/ -q
	@echo "=== web-app tests ==="
	cd services/web-app && npm test -- --watchAll=false

test-%: ## Test a specific service (e.g. make test-ai-engine)
	@echo "=== $* tests ==="
	@case "$*" in \
		market-data-service|trading-core-service) \
			cd services/$* && mvn test -q ;; \
		ai-engine) \
			cd services/ai-engine && python -m pytest tests/ -q ;; \
		web-app) \
			cd services/web-app && npm test -- --watchAll=false ;; \
	esac

# ── Database ──────────────────────────────────────────────────────────────────

db-shell: ## Open a psql shell to the database
	docker compose exec postgres psql -U trading_user -d trading_saas

db-reset: ## Drop and recreate the database (WARNING: destroys all data)
	docker compose exec postgres psql -U trading_user -c "DROP DATABASE IF EXISTS trading_saas;"
	docker compose exec postgres psql -U trading_user -c "CREATE DATABASE trading_saas;"
	docker compose exec postgres psql -U trading_user -d trading_saas -f /docker-entrypoint-initdb.d/init-schemas.sql

# ── Dev Setup ─────────────────────────────────────────────────────────────────

setup: ## First-time developer setup
	@bash scripts/setup-dev.sh

seed: ## Seed the database with sample data
	@bash scripts/seed-data.sh

# ── Clean ─────────────────────────────────────────────────────────────────────

clean: ## Remove build artifacts and containers (keeps volumes)
	$(DOCKER_COMPOSE) down --remove-orphans
	cd services/market-data-service && mvn clean -q 2>/dev/null || true
	cd services/trading-core-service && mvn clean -q 2>/dev/null || true
	find services/ai-engine -type d -name __pycache__ -exec rm -rf {} + 2>/dev/null || true
	find services/web-app -name .next -type d -exec rm -rf {} + 2>/dev/null || true

clean-all: clean ## Remove build artifacts, containers, AND volumes (full reset)
	$(DOCKER_COMPOSE) down --volumes --remove-orphans

# ── Jira Sync ─────────────────────────────────────────────────────────────────

jira-sync: ## Sync PLAN_EXECUTION.md to Jira (requires .env with JIRA_* vars)
	python scripts/jira-sync.py

jira-dry-run: ## Preview what would be synced to Jira (no API calls)
	python scripts/jira-sync.py --dry-run

jira-status: ## Update a Jira issue status: make jira-status TAG=E1-F01-PBI-01 STATUS="In Development"
	python scripts/jira-update-status.py "$(TAG)" "$(STATUS)"
