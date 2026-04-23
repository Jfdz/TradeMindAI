# market-data-service

Ingests OHLCV market data from Yahoo Finance, calculates technical indicators, and serves a REST API for historical prices and symbols.

## Overview

This service is the data backbone of the platform. It fetches daily OHLCV data for configured stock symbols, stores it in PostgreSQL (`market_data` schema), computes RSI, MACD, and SMA indicators using ta4j, caches latest prices in Redis, and publishes `market-data.prices.updated` events to RabbitMQ for downstream consumption by the AI engine.

## Tech Stack

- Java 21, Spring Boot 3.3
- Spring Data JPA + Flyway
- ta4j (technical indicators)
- MapStruct (domain ↔ JPA mapping)
- Redis (price caching)
- RabbitMQ (event publishing)

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- Docker (for local infra)

### Local development

```bash
# Start infra first
make infra-up

# Run the service
cd services/market-data-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Environment variables

| Variable | Description | Default |
|---|---|---|
| `POSTGRES_HOST` | PostgreSQL host | `localhost` |
| `POSTGRES_PORT` | PostgreSQL port | `5432` |
| `POSTGRES_USER` | Database user | `trading_user` |
| `POSTGRES_PASSWORD` | Database password | — |
| `REDIS_HOST` | Redis host | `localhost` |
| `RABBITMQ_HOST` | RabbitMQ host | `localhost` |

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/actuator/health` | No | Health check |
| `GET` | `/api/v1/symbols` | No | List tracked symbols |
| `GET` | `/api/v1/prices/{ticker}/history` | No | Historical OHLCV data |
| `GET` | `/api/v1/indicators/{ticker}` | No | Technical indicators |
| `POST` | `/api/v1/ingestion/trigger` | ADMIN | Manual ingestion trigger |

## Testing

```bash
mvn test
```

## Deployment

```bash
docker build -t market-data-service .
```
