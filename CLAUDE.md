# Trading SaaS - Development Progress

This file tracks Claude's progress through the PLAN_EXECUTION.md backlog.
Update after every completed PBI: record the last done task and the next in development.

---

## Feature Status

| Feature | Jira | Status |
|---|---|---|
| FEAT-04: Technical Indicators | SCRUM-204 | `Listo` |
| FEAT-05: Market Data REST API | SCRUM-208 | `Listo` |
| FEAT-06: AI Service Scaffold | SCRUM-213 | `Listo` |
| FEAT-07: CNN Model Implementation | SCRUM-220 | `Listo` |
| FEAT-08: Training Pipeline | SCRUM-227 | `Listo` |
| FEAT-09: Prediction API & Messaging | SCRUM-233 | `Listo` |

---

## Last Completed Task

**PBI:** `E2-F09-PBI-05` - Model management endpoints
**Feature:** FEAT-09: Prediction API & Messaging
**Epic:** EPIC-2: AI Engine
**Sprint:** S4
**Jira:** SCRUM-238 -> `Listo`
**Branch:** `feature-E2-F09-prediction-api`
**Completed:** 2026-04-16

### What was built (full FEAT-09)

| PBI | Jira | What was built | Status |
|---|---|---|---|
| E2-F09-PBI-01 | SCRUM-234 | `core/use_cases/prediction_service.py` + `adapters/in_/prediction.py` — `POST /api/v1/predict` | Listo |
| E2-F09-PBI-02 | SCRUM-235 | `POST /api/v1/predict/batch` — up to 50 tickers, single forward pass | Listo |
| E2-F09-PBI-03 | SCRUM-236 | `adapters/out/rabbitmq_consumer.py` — `PredictionRequestConsumer` on `ai-engine.prediction.requests` | Listo |
| E2-F09-PBI-04 | SCRUM-237 | `MarketDataEventConsumer` on `ai-engine.market-data.prices`, triggers predictions on `market-data.prices.updated` | Listo |
| E2-F09-PBI-05 | SCRUM-238 | `adapters/in_/models.py` — `GET /api/v1/models`, `GET /api/v1/models/active`, `POST /api/v1/models/{id}/activate` | Listo |

---

## Next In Development

**PBI:** `E3-F10-PBI-01` - User domain model
**Feature:** FEAT-10: Authentication System
**Epic:** EPIC-3: Trading Core
**Sprint:** S4
**Jira:** TBD -> `To Do`

### Acceptance criteria

- `User`, `Subscription`, `SubscriptionPlan` enum (FREE/BASIC/PREMIUM)
- Zero framework annotations on domain models; validation in constructors

---

## Backlog Queue (Sprint 1)

| PBI | Title | Status |
|---|---|---|
| E1-F01-PBI-01 | Initialize monorepo structure | Done |
| E1-F01-PBI-02 | Create .gitignore and Makefile | Done |
| E1-F01-PBI-03 | Docker Compose for local dev | Done |
| E1-F01-PBI-04 | Database schema initialization | Done |
| E1-F01-PBI-05 | Environment variable documentation | Done |
| E1-F02-PBI-01 | Spring Boot project scaffold | Done |
| E1-F02-PBI-02 | Domain models | In Development |
| E1-F02-PBI-03 | Port interfaces | To Do |
| E1-F02-PBI-04 | Flyway migrations | To Do |

