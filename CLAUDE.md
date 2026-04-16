# Trading SaaS - Development Progress

This file tracks Claude's progress through the PLAN_EXECUTION.md backlog.
Update after every completed PBI: record the last done task and the next in development.

---

## Last Completed Task

**PBI:** `E1-F04-PBI-03` - Indicator REST API
**Feature:** FEAT-04: Technical Indicators
**Epic:** EPIC-1: Foundation & Infrastructure
**Sprint:** S2
**Jira:** SCRUM-206 -> `Listo`
**Branch:** `feature-E1-F04-PBI-03-indicator-rest-api`
**Completed:** 2026-04-16

### What was built

| Task | Path | Status |
|---|---|---|
| T1: GetIndicatorsUseCase (port in) | `services/market-data-service/src/main/java/com/tradingsaas/marketdata/domain/port/in/GetIndicatorsUseCase.java` | Done |
| T2: GetIndicatorsUseCaseImpl | `services/market-data-service/src/main/java/com/tradingsaas/marketdata/application/usecase/GetIndicatorsUseCaseImpl.java` | Done |
| T3: IndicatorsController | `services/market-data-service/src/main/java/com/tradingsaas/marketdata/adapter/in/web/IndicatorsController.java` | Done |
| T4: IndicatorValueResponse / IndicatorsResponse DTOs | `services/market-data-service/src/main/java/com/tradingsaas/marketdata/adapter/in/web/dto/` | Done |
| T5: GetIndicatorsUseCaseImplTest | `services/market-data-service/src/test/java/com/tradingsaas/marketdata/application/usecase/GetIndicatorsUseCaseImplTest.java` | Done |
| T6: IndicatorsControllerTest | `services/market-data-service/src/test/java/com/tradingsaas/marketdata/adapter/in/web/IndicatorsControllerTest.java` | Done |

---

## Next In Development

**PBI:** `E1-F05-PBI-01` - Symbols endpoint
**Feature:** FEAT-05: Market Data REST API
**Epic:** EPIC-1: Foundation & Infrastructure
**Sprint:** S2
**Jira:** SCRUM-207 -> `To Do`

### Acceptance criteria

- `GET /api/v1/symbols` — paginated list of all tracked symbols (public)
- Returns ticker, name, exchange per symbol

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

