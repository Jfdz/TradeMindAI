# Trading SaaS

AI-powered trading signal SaaS platform with CNN-based stock predictions, subscription tiers, and backtesting.

## Overview

Trading SaaS is a microservices platform that delivers AI-generated trading signals for stock markets. It ingests real market data, processes it through a CNN model, and serves BUY/SELL/HOLD signals to subscribers via a Next.js dashboard.

## Architecture

```
trading_saas/
├── services/
│   ├── market-data-service/   # Java/Spring Boot — OHLCV ingestion + indicators (:8081)
│   ├── trading-core-service/  # Java/Spring Boot — Auth, signals, strategies (:8082)
│   ├── ai-engine/             # Python/FastAPI/PyTorch — CNN inference (:8000)
│   └── web-app/               # Next.js 14 — Dashboard frontend (:3000)
├── infrastructure/
│   ├── k8s/                   # Kubernetes manifests
│   └── init-schemas.sql       # PostgreSQL schema initialization
├── shared/
│   └── api-specs/             # OpenAPI specs (shared between services)
├── scripts/                   # Dev tooling, seed data, Jira sync
└── .github/workflows/         # CI/CD pipelines per service
```

## Tech Stack

| Layer | Technology |
|---|---|
| Market data | Java 21, Spring Boot 3, Flyway, ta4j |
| Trading core | Java 21, Spring Boot 3, Spring Security, JWT |
| AI engine | Python 3.11, FastAPI, PyTorch, pandas |
| Frontend | Next.js 14, TypeScript, Tailwind, shadcn/ui |
| Database | PostgreSQL 16 (3 schemas) |
| Cache | Redis 7 |
| Messaging | RabbitMQ 3.13 |
| Infra | Docker Compose (dev), Kubernetes (prod) |

## Getting Started

### Prerequisites

- Docker Desktop 4.x+
- Java 21 (for local service development)
- Python 3.11+ (for AI engine development)
- Node.js 20+ (for frontend development)
- Make

### Local development

```bash
# Copy environment config
cp .env.example .env

# Start infrastructure (PostgreSQL, Redis, RabbitMQ)
make infra-up

# Start all services
make up

# Run all tests
make test
```

### Environment variables

All variables are documented in `.env.example`. Copy it to `.env` and fill in your values.

## Services

| Service | Port | Description |
|---|---|---|
| market-data-service | :8081 | OHLCV ingestion, indicators, REST API |
| trading-core-service | :8082 | Auth, signals, strategies, backtesting |
| ai-engine | :8000 | CNN inference, training, model management |
| web-app | :3000 | Next.js dashboard |
| PostgreSQL | :5432 | Primary database |
| Redis | :6379 | Cache and rate limiting |
| RabbitMQ | :5672 / :15672 | Async messaging / Management UI |

## Development

See each service's README for service-specific setup and development instructions.

## Deployment

See `infrastructure/k8s/` for Kubernetes manifests and the CI/CD pipelines in `.github/workflows/`.

## License

Private — all rights reserved.
