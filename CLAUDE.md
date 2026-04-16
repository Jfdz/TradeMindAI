# Trading SaaS - Development Progress

This file tracks Claude's progress through the PLAN_EXECUTION.md backlog.
Update after every completed PBI: record the last done task and the next in development.

---

## Feature Status

| Feature | Jira | Status |
|---|---|---|
| FEAT-04: Technical Indicators | SCRUM-203 | `Listo` |
| FEAT-05: Market Data REST API | SCRUM-204 | `In Development` |

---

## Last Completed Task

**PBI:** `E1-F05-PBI-01` - Symbols endpoint
**Feature:** FEAT-05: Market Data REST API
**Epic:** EPIC-1: Foundation & Infrastructure
**Sprint:** S2
**Jira:** SCRUM-207 -> `Listo`
**Branch:** `feature-E1-F05-PBI-01-symbols-endpoint`
**Completed:** 2026-04-16

### What was built

| Task | Path | Status |
|---|---|---|
| T1: SymbolRepository (port out) | `domain/port/out/SymbolRepository.java` | Done |
| T2: SymbolJpaRepository + SymbolRepositoryAdapter | `adapter/out/persistence/` | Done |
| T3: GetSymbolsUseCase + Impl | `domain/port/in/` + `application/usecase/` | Done |
| T4: SymbolResponse + PagedResponse DTOs | `adapter/in/web/dto/` | Done |
| T5: SymbolsController | `adapter/in/web/SymbolsController.java` | Done |
| T6: GetSymbolsUseCaseImplTest + SymbolsControllerTest | `src/test/` | Done |

---

## Next In Development

**PBI:** `E1-F05-PBI-02` - Historical prices endpoint
**Feature:** FEAT-05: Market Data REST API
**Epic:** EPIC-1: Foundation & Infrastructure
**Sprint:** S2
**Jira:** SCRUM-208 -> `In Development`

### Acceptance criteria

- `GET /api/v1/prices/{ticker}/history?from=&to=&timeframe=DAILY`
- Returns paginated OHLCV data sorted by date DESC

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

