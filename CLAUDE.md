# Trading SaaS - Development Progress

This file tracks Claude's progress through the PLAN_EXECUTION.md backlog.
Update after every completed PBI: record the last done task and the next in development.

---

## Environment Notes

- Maven launcher: `C:\Users\fakdu\tools\apache-maven-3.9.10\bin\mvn.cmd`
- JDK 21 home: `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- Use `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot` when running Maven in this shell.

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
| FEAT-10: Authentication System | SCRUM-240 | `Listo` |
| FEAT-11: Subscription & Rate Limiting | SCRUM-249 | `Listo` |
| FEAT-12: Signal Generation Engine | SCRUM-252 | `Listo` |
| FEAT-13: Strategy & Risk Management | SCRUM-259 | `Listo` |
| FEAT-14: Frontend Scaffold & Auth | SCRUM-264 | `Listo` |
| FEAT-15: Landing & Marketing Pages | SCRUM-271 | `Listo` |
| FEAT-16: Dashboard Core | SCRUM-274 | `Listo` |
| FEAT-17: Charts & Visualization | SCRUM-281 | `Listo` |
| FEAT-18: Backtesting Engine Core | SCRUM-285 | `Listo` |
| FEAT-19: Backtest Configuration Form | SCRUM-290 | `Listo` |
| FEAT-20: Docker & Container Optimization | SCRUM-298 | `Listo` |
| FEAT-21: Kubernetes Deployment | SCRUM-301 | `Listo` |
| FEAT-22: CI/CD Pipelines | SCRUM-307 | `Listo` |
| FEAT-23: Observability | SCRUM-311 | `Listo` |
| FEAT-24: Security Hardening | SCRUM-316 | `Listo` |

---

## Sprint 8 — COMPLETE

All Sprint 8 PBIs delivered. EPIC-6 (Production & DevOps) is fully done.

---

## Sprint 9 — Security & Quality Hardening (Closed)

Codex full-repo audit (2026-04-28) found 21 flaws across 3 severity tiers. This sprint fixes them all.
Branch: `fix/security-quality-hardening`

### Done Tasks

| ID | Severity | Title | Status |
|---|---|---|---|
| FIX-01 | Critical | Purge committed secrets — `.env` confirmed untracked; real `JIRA_API_TOKEN` redacted locally. **Action required: revoke old token at id.atlassian.com** | Done |
| FIX-02 | Critical | Strip refresh tokens from response bodies & session state | Done |
| FIX-03 | Critical | Add ownership check to backtest jobs | Done |
| FIX-04 | High | Add `InternalSecretFilter` — `/api/v1/ingestion/**` requires `X-Internal-Secret` header | Done |
| FIX-05 | High | Restrict actuator `show-details`/`show-components` to `never` in both services | Done |
| FIX-06 | High | Replace JPQL `LIMIT 1` with `Pageable`-based query in `StockPriceJpaRepository` | Done |
| FIX-07 | High | Fix broken `APP_CORS_ALLOWED_ORIGINS` env interpolation in `docker-compose.prod.yml` | Done |
| FIX-08 | High | Grafana prod credentials now mandatory (`:?` syntax — fail startup if unset) | Done |
| FIX-09 | High | Remove all demo data fallbacks from `api-client.ts` — errors propagate to UI | Done |
| FIX-10 | High | Add `services.market-data.url` to `trading-core` `application.yml` mapped from env var | Done |

---

| FIX-11 | High | Persist backtest jobs in DB via JPA/Flyway; in-memory store kept for tests only | Done |
| FIX-12 | Medium | Extract `ManagePortfolioPositionUseCase` — JPA entity no longer in controller | Done |
| FIX-13 | Medium | Batch latest-price API added; portfolio valuation now uses one batched lookup | Done |
| FIX-14 | Medium | Save + outbox insert now transactional; cache is after-commit; scheduled relay publishes pending events | Done |
| FIX-15 | Medium | Clamp negative `page` param in `StockPricesController` | Done |
| FIX-16 | Medium | Map `DataIntegrityViolationException` → 409 in `GlobalExceptionHandler` | Done |
| FIX-17 | Medium | Validate backtest `from ≤ to` and max 5-year window | Done |
| FIX-18 | Medium | Cap pageable `max-page-size: 100` via Spring config for signals/strategies | Done |
| FIX-19 | Medium | Vitest coverage added for middleware, auth, and api-client fallback flows | Done |
| FIX-20 | Low | K8s kustomization `images` block centralizes tag overrides; `imagePullPolicy: IfNotPresent` | Done |
| FIX-21 | Low | Remove `unsafe-eval` from CSP; narrow `connect-src` to exact origins | Done |

---

### Sprint 9 — CLOSED

All critical and high fixes merged. FIX-11, FIX-13, FIX-14, and FIX-19 are now completed. No deferred FIX items remain.

### Remaining Tasks

None.

### Next To Do

1. Open PR from `fix/security-quality-hardening` -> `develop`.

Open PR from `fix/security-quality-hardening` -> `develop`.
| FIX-03 | Critical | Add ownership check to backtest jobs | `BacktestController.java:53-63`, `InMemoryBacktestJobStore.java:17-27` |
| FIX-04 | High | Require ADMIN auth on ingestion endpoints | `SecurityConfig.java:37-38`, `IngestionController.java:28-31` |
| FIX-05 | High | Restrict actuator exposure (no public full-details) | `application.yml:83-84` (both services) |
| FIX-06 | High | Fix `LIMIT 1` in JPQL query | `StockPriceJpaRepository.java:43-49` |
| FIX-07 | High | Fix broken env var interpolation in prod compose | `docker-compose.prod.yml:115` |
| FIX-08 | High | Remove hardcoded `admin/admin` Grafana credentials | `docker-compose.prod.yml:311-312` |
| FIX-09 | High | Remove demo data fallbacks from API client | `web-app/lib/api-client.ts:169-301,327-409` |
| FIX-10 | High | Fix `market-data.url` property wiring | `MarketDataServiceAdapter.java:21`, all service YAMLs |
| FIX-11 | High | Replace unbounded thread pool; persist backtest jobs to DB | `DefaultBacktestExecutionService.java:24,41,51-52` |
| FIX-12 | Medium | Remove JPA entity from PortfolioController | `PortfolioController.java:3-4,41,82-89,96-123` |
| FIX-13 | Medium | Batch N+1 market-data calls in portfolio valuation | `PortfolioOverviewService.java:42-44,111-117` |
| FIX-14 | Medium | Wrap save+cache+publish in transaction / outbox | `FetchMarketDataUseCaseImpl.java:36-40` |
| FIX-15 | Medium | Validate `page` param; standardize error bodies in StockPricesController | `StockPricesController.java:45,49,53,60,73` |
| FIX-16 | Medium | Handle email uniqueness race condition → 409 | `RegisterUserService.java:28-50`, `GlobalExceptionHandler.java:42-45,91-94` |
| FIX-17 | Medium | Validate backtest date range (`from ≤ to`, max window) | `BacktestController.java:75-79` |
| FIX-18 | Medium | Cap page size on signals & strategies endpoints | `SignalController.java:30`, `StrategyController.java:37-39` |
| FIX-19 | Medium | Add frontend tests (middleware, auth, api-client) | `services/web-app/` |
| FIX-20 | Low | Pin versioned image tags in K8s manifests | `infrastructure/k8s/base/*.yml:26-27` |
| FIX-21 | Low | Remove `unsafe-eval` from CSP; narrow `connect-src` | `next.config.mjs:36-37` |

---

## Last Completed Task (Sprint 9)

**PBI:** `FIX-19` - Frontend tests for middleware, auth, and api-client
**Feature:** FEAT-24: Security Hardening
**Epic:** EPIC-6: Production & DevOps
**Sprint:** S9
**Jira:** `Closed`
**Branch:** `fix/security-quality-hardening`
**Completed:** 2026-04-28

### What was completed

| Acceptance criterion | Status |
|---|---|
| Vitest is configured in `web-app` and wired to `npm test` | Closed |
| Middleware, auth credential flow, and api-client fallback/header behavior are covered by unit tests | Closed |
| Frontend unit test suite passes locally | Closed |

### Remaining Tasks

None.

---

## Next In Development

None.

Next to do: open PR from `fix/security-quality-hardening` -> `develop`.


## Backlog Queue (Sprint 8)

| PBI | Title | Status |
|---|---|---|
| E6-F20-PBI-01 | Production Docker Compose | Listo |
| E6-F20-PBI-02 | Docker image scanning | Listo |
| E6-F21-PBI-01 | K8s Deployments for all services | Listo |
| E6-F21-PBI-02 | HPA for auto-scaling | Listo |
| E6-F21-PBI-03 | Ingress + TLS | Listo |
| E6-F21-PBI-04 | NetworkPolicies | Listo |
| E6-F21-PBI-05 | ConfigMaps and Secrets | Listo |
| E6-F22-PBI-01 | GitHub Actions per service | Listo |
| E6-F22-PBI-02 | Staging deploy pipeline | Listo |
| E6-F22-PBI-03 | Production deploy pipeline | Listo |
| E6-F23-PBI-01 | Structured JSON logging | Listo |
| E6-F23-PBI-02 | Prometheus metrics | Listo |
| E6-F23-PBI-03 | Grafana dashboards | Listo |
| E6-F23-PBI-04 | Distributed tracing | Listo |
| E6-F24-PBI-01 | OWASP dependency check in CI | Listo |
| E6-F24-PBI-02 | Security headers | Listo |
| E6-F24-PBI-03 | CORS configuration | Listo |
| E6-F24-PBI-04 | Load testing with k6 | Listo |








