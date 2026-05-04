# Repository Guidelines

## Project Structure & Module Organization
This repository is a multi-service trading platform. Core application code lives under `services/`:
- `market-data-service/`: Java 21 + Spring Boot market ingestion and indicators
- `trading-core-service/`: Java 21 + Spring Boot auth, signals, subscriptions, backtesting
- `ai-engine/`: Python 3.11 + FastAPI + PyTorch inference and training
- `web-app/`: Next.js App Router frontend

Shared assets live in `shared/api-specs/`. Deployment and ops files are in `infrastructure/`, and developer utilities live in `scripts/`.

## Build, Test, and Development Commands
Use the top-level `Makefile` for common workflows:
- `make infra-up`: start PostgreSQL, Redis, and RabbitMQ
- `make up`: start the full stack with Docker Compose
- `make test`: run all service test suites
- `make test-web-app` or `make test-ai-engine`: run one service's tests
- `make logs-web-app`: tail logs for a specific service

Service-local commands:
- `cd services/web-app && npm run dev`
- `cd services/ai-engine && uvicorn ai_engine.main:app --reload --port 8000`
- `cd services/trading-core-service && mvn test`

## Coding Style & Naming Conventions
Follow existing service conventions:
- Java: 4-space indentation, `PascalCase` classes, `camelCase` methods, package names under `com.tradingsaas.*`
- Python: PEP 8 with Black/Ruff, max line length `100`, `snake_case` modules and functions
- TypeScript/React: 2-space indentation, double quotes, `PascalCase` components, `camelCase` utilities

Run `npm run lint` in `services/web-app`. For Python changes, use Black, Ruff, and MyPy settings from `services/ai-engine/pyproject.toml`.

## Testing Guidelines
Place tests beside each service's standard test tree:
- Java: `src/test/java`, test classes ending in `*Test.java`
- Python: `tests/unit/` and `tests/integration/`, files named `test_*.py`
- Web app: Vitest `*.test.ts` files near the code they cover

Prefer targeted runs before `make test`, and cover both happy paths and integration boundaries for service-to-service behavior.

## Commit & Pull Request Guidelines
Recent history follows short conventional subjects such as `fix: ...` and `ci: ...`. Use imperative, scoped commit messages and keep merges clean.

PRs should include:
- a short description of the user-visible or system-level change
- linked issue or planning artifact when applicable
- test evidence (`make test`, `mvn test`, `pytest`, or `npm test`)
- screenshots for frontend changes
- notes for config, schema, or environment variable updates

## Security & Configuration Tips
Do not commit secrets. Start from `.env.example`, keep local overrides in `.env`, and use the templates under `infrastructure/k8s/base/` for deployment configuration.
