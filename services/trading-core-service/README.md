# trading-core-service

Core business service handling JWT authentication, subscription tiers, AI-powered trading signal generation, and strategy risk management.

## Overview

This service is the business heart of the platform. It manages user accounts, authenticates with JWT, enforces subscription-based rate limiting, orchestrates signal generation by calling the AI engine, and provides strategy CRUD with risk parameter management.

## Tech Stack

- Java 21, Spring Boot 3.3
- Spring Security 6 + JWT (jjwt)
- Spring Data JPA + Flyway
- Resilience4j (circuit breaker, retry)
- bucket4j + Redis (rate limiting)
- RabbitMQ (prediction event listener)

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker (for local infra)

### Local development

```bash
make infra-up
cd services/trading-core-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Environment variables

| Variable | Description | Default |
|---|---|---|
| `POSTGRES_HOST` | PostgreSQL host | `localhost` |
| `JWT_SECRET` | JWT signing key (min 32 chars) | — |
| `REDIS_HOST` | Redis host | `localhost` |
| `AI_ENGINE_SERVICE_URL` | AI engine URL | `http://localhost:8000` |

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | No | User registration |
| `POST` | `/api/v1/auth/login` | No | JWT login |
| `POST` | `/api/v1/auth/refresh` | No | Token refresh |
| `POST` | `/api/v1/auth/logout` | Yes | Logout + blacklist |
| `GET` | `/api/v1/signals` | Yes | List signals (paginated) |
| `GET` | `/api/v1/signals/{id}` | Yes | Single signal |
| `POST` | `/api/v1/strategies` | Yes | Create strategy |
| `GET` | `/api/v1/strategies` | Yes | List user strategies |
| `PUT` | `/api/v1/strategies/{id}` | Yes | Update strategy |
| `DELETE` | `/api/v1/strategies/{id}` | Yes | Delete strategy |

## Testing

```bash
mvn test
```

## Deployment

```bash
docker build -t trading-core-service .
```
