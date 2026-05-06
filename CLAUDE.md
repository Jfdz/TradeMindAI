# Trading SaaS - Development Progress

File track Claude progress through PLAN_EXECUTION.md backlog.
Update after every done PBI: record last done task and next in development.

---

## Environment Notes

- Maven resolution rule: prefer repo-local Maven first if visible and runnable (`mvnw.cmd`, `mvnw`, or other repo-provided wrapper/script). Use installed Maven path below only as fallback when repo no provide own Maven entrypoint.
- Maven fallback launcher: `C:\Users\JFERNANDEZ\tools\apache-maven-3.9.10\bin\mvn.cmd`
- JDK 21 home: `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- Use `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot` when running Maven in this shell.
- Add Maven for current shell before running tests:
  `$env:Path='C:\Users\JFERNANDEZ\tools\apache-maven-3.9.10\bin;' + $env:JAVA_HOME + '\bin;' + $env:Path`

--

### What was completed

| Acceptance criterion | Status |
|---|---|
| Vitest configured in `web-app` and wired to `npm test` | Closed |
| Middleware, auth credential flow, and api-client fallback/header behavior covered by unit tests | Closed |
| Frontend unit test suite passes locally | Closed |

### Remaining Tasks

None.

---

## Sprint 11 - Architecture Logic Remediation

**Branch:** `fix/sprint11-architecture-remediation`
**Started:** 2026-04-29
**Coverage target for new code:** 85% min via focused unit tests per slice.

### Current Status

| ID | Title | Status | Notes |
|---|---|---|---|
| S11-01 | Make market-data internal-only | Done | `market-data-service` now require `X-Internal-Secret` for all `/api/v1/**`; health/actuator stay public. Docker Compose already no host port. K8s service stay `ClusterIP`; ingress no longer route prices/symbols to market-data. |
| S11-02 | Move market-data calls into trading-core | Done | Added trading-core proxy endpoints for `/api/v1/prices/**` and `/api/v1/symbols`; trading-core adapter send `X-Internal-Secret`; auth and rate-limit integration coverage now pass locally for proxy routes. |
| S11-03 | Subscription tier enforcement at every paid endpoint | In Progress | Historical price windows, backtest submission, and active strategy quotas now enforce tier access in trading-core. Subscription usage ledger now record allowed/denied requests for gated history, backtest, and strategy mutation flows. Next: finish remaining S11-03 durability gap by reconciling Redis hot-path counters with ledger and extend same pattern to any new paid endpoints. |

### Completed This Session

| Change | Files |
|---|---|
| Protected all market-data `/api/v1/**` routes with internal secret filter | `InternalSecretFilter.java`, `SecurityConfig.java`, `SecurityConfigTest.java` |
| Added trading-core market-data proxy facade | `MarketDataProxyController.java`, `MarketDataServiceAdapter.java`, `application.yml` |
| Removed direct K8s ingress access to market-data price/symbol APIs | `infrastructure/k8s/base/ingress.yml` |
| Wired internal market-data service config/secrets for Compose and K8s | `docker-compose.yml`, `configmaps.yml`, service deployment YAMLs, `internal-service-secret-template.yml` |
| Added focused tests for new Sprint 11 code | `MarketDataServiceAdapterTest.java`, `MarketDataProxyControllerTest.java`, updated `SecurityConfigTest.java` |
| Added trading-core auth/rate-limit integration coverage for market-data proxy routes | `MarketDataProxySecurityTest.java` |
| Added first subscription-tier enforcement slice for historical market-data access | `SubscriptionAccessGuard.java`, `GlobalExceptionHandler.java`, `MarketDataProxyController.java`, `SubscriptionAccessGuardTest.java`, updated `MarketDataProxyControllerTest.java`, updated `MarketDataProxySecurityTest.java` |
| Enforced BASIC+ access for backtest submission with aspect-backed MVC coverage | `BacktestController.java`, `RequiresSubscriptionAspect.java`, `SecurityConfig.java`, `BacktestSubscriptionSecurityTest.java` |
| Enforced active-strategy quotas by plan and covered strategy create path in MVC/security tests | `StrategyManagementService.java`, `StrategyRepository.java`, `StrategyJpaRepository.java`, `StrategyRepositoryAdapter.java`, `ManageStrategiesUseCase.java`, `StrategyManagementServiceTest.java`, `StrategySubscriptionSecurityTest.java`, updated `StrategyControllerTest.java` |
| Added durable subscription usage ledger tracking for gated entitlement routes | `V11__create_subscription_usage_ledger_table.sql`, `SubscriptionUsageLedgerJpaEntity.java`, `SubscriptionUsageLedgerJpaRepository.java`, `SubscriptionUsageLedgerService.java`, `SubscriptionUsageLedgerInterceptor.java`, `SubscriptionUsageLedgerWebConfig.java`, `SubscriptionUsageLedgerServiceTest.java`, updated `MarketDataProxySecurityTest.java`, updated `BacktestSubscriptionSecurityTest.java`, updated `StrategySubscriptionSecurityTest.java` |

### Verification

| Check | Status |
|---|---|
| Static check: frontend has no `NEXT_PUBLIC_MARKET_DATA_URL` references | Passed |
| Static check: K8s ingress has no `market-data-service` route for `/api/v1/prices` or `/api/v1/symbols` | Passed |
| Tooling install | Passed - installed Temurin 21 and Maven 3.9.10 on current machine |
| Targeted market-data tests | Passed - `SecurityConfigTest`, `InternalSecretFilterTest`, `IngestionControllerTest` |
| Targeted trading-core tests | Passed - `MarketDataServiceAdapterTest`, `MarketDataProxyControllerTest` |
| Trading-core proxy auth/rate-limit integration tests | Passed - `MarketDataProxySecurityTest` |
| Trading-core subscription access tests | Passed - `SubscriptionAccessGuardTest`, updated `MarketDataProxySecurityTest`, updated `MarketDataProxyControllerTest` |
| Trading-core backtest subscription tests | Passed - `BacktestSubscriptionSecurityTest`, `BacktestControllerTest` |
| Trading-core strategy quota tests | Passed - `StrategyManagementServiceTest`, `StrategyControllerTest`, `StrategySubscriptionSecurityTest` |
| Trading-core subscription usage ledger tests | Passed - `SubscriptionUsageLedgerServiceTest`, updated `MarketDataProxySecurityTest`, updated `BacktestSubscriptionSecurityTest`, updated `StrategySubscriptionSecurityTest` |
| Trading-core Flyway migration validation for usage ledger | Added - `FlywayMigrationTest` is assumption-guarded and skipped locally when Docker unavailable |
| Maven local cache recovery | Passed - removed two corrupted `.m2` POM dirs and re-ran successfully |

### Next In Development

**PBI:** `S11-03` - subscription tier enforcement at every paid endpoint
**Immediate next task:** Finish remaining `S11-03` durability work by reconciling Redis hot-path counters with `subscription_usage_ledger`, and keep applying same entitlement + ledger pattern to any new paid endpoints before moving to `S11-04`.

---

## Next In Development
Tracker note (2026-04-28): `S10-14`, `S10-17`, `S10-18`, `S10-19`, and `S10-24` done in working tree below. Remaining active sprint item: `S10-10`, then `S10-11`.

**PBI:** `S10-01` — AI engine: add auth middleware on training/model routes
**Branch:** `fix/sprint10-flaw-remediation`
**Severity:** Critical

### Acceptance Criteria
- Given unauth request to `/api/v1/models/**` or `/api/v1/training/**`, When arrives, Then service returns 401.
- Given request with valid admin token, When arrives, Then route executes normally.
- Unit tests cover auth dependency.


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