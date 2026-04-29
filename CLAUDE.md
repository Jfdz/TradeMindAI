# Trading SaaS - Development Progress

This file tracks Claude's progress through the PLAN_EXECUTION.md backlog.
Update after every completed PBI: record the last done task and the next in development.

---

## Environment Notes

- Maven launcher: `C:\Users\fakdu\tools\apache-maven-3.9.10\bin\mvn.cmd`
- JDK 21 home: `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- Use `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot` when running Maven in this shell.

--

### What was completed

| Acceptance criterion | Status |
|---|---|
| Vitest is configured in `web-app` and wired to `npm test` | Closed |
| Middleware, auth credential flow, and api-client fallback/header behavior are covered by unit tests | Closed |
| Frontend unit test suite passes locally | Closed |

### Remaining Tasks

None.

---

## Sprint 11 - Architecture Logic Remediation

**Branch:** `fix/sprint11-architecture-remediation`
**Started:** 2026-04-29
**Coverage target for new code:** 85% minimum via focused unit tests for each implemented slice.

### Current Status

| ID | Title | Status | Notes |
|---|---|---|---|
| S11-01 | Make market-data internal-only | Done - pending local Maven verification | `market-data-service` now requires `X-Internal-Secret` for all `/api/v1/**`; health/actuator stays public. Docker Compose already had no host port. K8s service remains `ClusterIP`; ingress no longer routes prices/symbols to market-data. |
| S11-02 | Move market-data calls into trading-core | In Development | Added trading-core proxy endpoints for `/api/v1/prices/**` and `/api/v1/symbols`; trading-core adapter sends `X-Internal-Secret`; frontend already uses only `NEXT_PUBLIC_API_BASE_URL`. |
| S11-03 | Subscription tier enforcement at every paid endpoint | Next | Implement `@RequireTier` or equivalent interceptor, ledger table, and tests after S11-02 test verification passes. |

### Completed This Session

| Change | Files |
|---|---|
| Protected all market-data `/api/v1/**` routes with internal secret filter | `InternalSecretFilter.java`, `SecurityConfig.java`, `SecurityConfigTest.java` |
| Added trading-core market-data proxy facade | `MarketDataProxyController.java`, `MarketDataServiceAdapter.java`, `application.yml` |
| Removed direct K8s ingress access to market-data price/symbol APIs | `infrastructure/k8s/base/ingress.yml` |
| Wired internal market-data service config/secrets for Compose and K8s | `docker-compose.yml`, `configmaps.yml`, service deployment YAMLs, `internal-service-secret-template.yml` |
| Added focused tests for new Sprint 11 code | `MarketDataServiceAdapterTest.java`, `MarketDataProxyControllerTest.java`, updated `SecurityConfigTest.java` |

### Verification

| Check | Status |
|---|---|
| Static check: frontend has no `NEXT_PUBLIC_MARKET_DATA_URL` references | Passed |
| Static check: K8s ingress has no `market-data-service` route for `/api/v1/prices` or `/api/v1/symbols` | Passed |
| Targeted Maven tests | Blocked locally - no `mvn`, `mvn.cmd`, or `mvnw.cmd` found on this machine; documented Maven path under `C:\Users\fakdu\...` does not exist for current user. |

### Next In Development

**PBI:** `S11-02` - finish verification and harden proxy/rate-limit behavior
**Immediate next task:** Install or point to a valid Maven executable, run targeted tests, then add integration coverage for authenticated price/symbol rate limiting before moving to `S11-03`.

---

## Next In Development
Tracker note (2026-04-28): `S10-14`, `S10-17`, `S10-18`, `S10-19`, and `S10-24` are completed in the working tree below. The remaining active sprint item is `S10-10`, followed by `S10-11`.

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
| S10-14 | High | Persist AI engine training run state in shared DB | `adapters/in_/training.py:13,40-51`, `k8s/base/ai-engine.yml:11` | Done |
| S10-15 | High | Fix backend/frontend contract for backtest symbol availability | `BacktestController.java:40-43`, `api-client.ts:259-265` | Done |
| S10-16 | High | Add retry cap + dead-letter queue to outbox relay | `MarketDataOutboxRelay.java:22-41`, `MarketDataOutboxService.java:42-45,66-83` | Done |
| S10-17 | Medium | Add TanStack Query; replace all `useEffect` server-state fetches | `web-app/package.json`, `dashboard/page.tsx:237-347`, `signals/page.tsx:126-164` | Done |
| S10-18 | Medium | Add `error.tsx` and `loading.tsx` to all route segments | `dashboard/page.tsx:366-395`, `signal-detail-client.tsx:195-225` | Done |
| S10-19 | Medium | Batch per-symbol dashboard requests server-side | `dashboard/page.tsx:242-307` | Done |
| S10-20 | Medium | Remove `unsafe-inline`/`unsafe-eval` from CSP | `next.config.mjs:35-37` | Done |
| S10-21 | Medium | Fix JWT env var name mismatch between K8s ConfigMap and Spring | `k8s/base/configmaps.yml:30-31`, `trading-core/application.yml:99-101` | Done |
| S10-22 | Medium | Pin immutable image tags in all K8s and Compose files | K8s base YAMLs, `docker-compose.yml`, `docker-compose.prod.yml` | Done |
| S10-23 | Medium | Drive rate-limit tiers from config; fix refill window to per-minute | `RateLimitFilter.java:28-31,66-73` | Done |
| S10-24 | Medium | Add controller tests for auth cookie handling and user/profile endpoints | `trading-core-service/src/test/java` | Done |
| S10-25 | Medium | Remove demo data fallbacks from production API client paths | `web-app/lib/api-client.ts:245-406` | Done |
| S10-26 | Low | Extract shared signal-formatting utilities | `dashboard/page.tsx:89-147`, `signals/page.tsx:60-118`, `signal-detail-client.tsx:47-63` | Done |

---







