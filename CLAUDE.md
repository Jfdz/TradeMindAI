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

**PBI:** `S10-01` — AI engine: add auth middleware on training/model routes
**Branch:** `fix/sprint10-flaw-remediation`
**Severity:** Critical

### Acceptance Criteria
- Given an unauthenticated request to `/api/v1/models/**` or `/api/v1/training/**`, When it arrives, Then the service returns 401.
- Given a request with a valid admin token, When it arrives, Then the route is executed normally.
- Unit tests cover the auth dependency.


---

## Sprint 10 — Codex Full-Repo Audit Remediation (2026-04-28)

Second Codex audit found 26 flaws. Branch: `fix/sprint10-flaw-remediation`

### Backlog Queue

| ID | Severity | Title | File(s) | Status |
|---|---|---|---|---|
| S10-01 | Critical | Add auth middleware on AI engine training/model routes | `ai_engine/main.py:223-236`, `adapters/in_/training.py`, `adapters/in_/models.py` | Done |
| S10-02 | Critical | Require auth/role on market-data ingestion endpoint; remove host port | `SecurityConfig.java:36-38`, `IngestionController.java:28-36`, `docker-compose.yml:77-78` | Done |
| S10-03 | Critical | Fix RabbitMQ message contract mismatch + bind queue to exchange | `RabbitMqMarketDataEventPublisher.java:29-37`, `rabbitmq_consumer.py:109-124` | Done |
| S10-04 | Critical | Fix SQL injection in AI engine `load_ohlcv()` | `db_adapter.py:36-45` | Done |
| S10-05 | High | Add `SameSite=Lax` to refresh cookie; protect CSRF | `SecurityConfig.java:57-58`, `AuthController.java:102-117` | Done |
| S10-06 | High | Gate Prometheus/metrics actuator behind auth on both Java services | `trading-core/application.yml:76-84`, `market-data/application.yml:76-84` | Done |
| S10-07 | High | Fix JPQL `LIMIT 1` — use `findFirstBy...OrderByDateDesc` | `StockPriceJpaRepository.java:43-51` | Done |
| S10-08 | High | Move portfolio mutations behind use-case layer | `PortfolioController.java:3-4,41-49,82-123` | Done |
| S10-09 | High | Replace unbounded `newCachedThreadPool` with bounded executor | `DefaultBacktestExecutionService.java:22-25,41-42` | Done |
| S10-10 | High | Fail startup or block readiness if AI engine migration/consumer fails | `ai_engine/main.py:40-47,75-117` | In Development |
| S10-11 | High | Proxy backend `Set-Cookie` refresh token through Next.js route handler | `web-app/lib/auth.ts:31-62`, `AuthController.java:63-66,111-117` | To Do |
| S10-12 | High | Fix `APP_CORS_ALLOWED_ORIGINS` interpolation in `docker-compose.prod.yml` | `docker-compose.prod.yml:115` | Done |
| S10-13 | High | Remove `.env` with real credentials from version control | `.env:21,42-43,73,77,190-192` | Done (already untracked) |
| S10-14 | High | Persist AI engine training run state in shared DB | `adapters/in_/training.py:13,40-51`, `k8s/base/ai-engine.yml:11` | To Do |
| S10-15 | High | Fix backend/frontend contract for backtest symbol availability | `BacktestController.java:40-43`, `api-client.ts:259-265` | Done |
| S10-16 | High | Add retry cap + dead-letter queue to outbox relay | `MarketDataOutboxRelay.java:22-41`, `MarketDataOutboxService.java:42-45,66-83` | Done |
| S10-17 | Medium | Add TanStack Query; replace all `useEffect` server-state fetches | `web-app/package.json`, `dashboard/page.tsx:237-347`, `signals/page.tsx:126-164` | To Do |
| S10-18 | Medium | Add `error.tsx` and `loading.tsx` to all route segments | `dashboard/page.tsx:366-395`, `signal-detail-client.tsx:195-225` | To Do |
| S10-19 | Medium | Batch per-symbol dashboard requests server-side | `dashboard/page.tsx:242-307` | To Do |
| S10-20 | Medium | Remove `unsafe-inline`/`unsafe-eval` from CSP | `next.config.mjs:35-37` | Done |
| S10-21 | Medium | Fix JWT env var name mismatch between K8s ConfigMap and Spring | `k8s/base/configmaps.yml:30-31`, `trading-core/application.yml:99-101` | Done |
| S10-22 | Medium | Pin immutable image tags in all K8s and Compose files | K8s base YAMLs, `docker-compose.yml`, `docker-compose.prod.yml` | Done |
| S10-23 | Medium | Drive rate-limit tiers from config; fix refill window to per-minute | `RateLimitFilter.java:28-31,66-73` | Done |
| S10-24 | Medium | Add controller tests for auth cookie handling and user/profile endpoints | `trading-core-service/src/test/java` | To Do |
| S10-25 | Medium | Remove demo data fallbacks from production API client paths | `web-app/lib/api-client.ts:245-406` | Done |
| S10-26 | Low | Extract shared signal-formatting utilities | `dashboard/page.tsx:89-147`, `signals/page.tsx:60-118`, `signal-detail-client.tsx:47-63` | Done |

---

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








