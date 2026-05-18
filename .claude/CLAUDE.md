# TradeMindAI — Claude Operating Manual

Authoritative guide for any AI assistant in this repo. Non-negotiable unless explicit human instruction supersedes section.

---

## 1. System Identity

TradeMindAI = polyglot microservice trading-signal SaaS. Four runtime processes, one source of truth (PostgreSQL), one async fabric (RabbitMQ), one hot cache (Redis). Platform must stay **stateless at edge, deterministic at core, auditable end-to-end**. Every change judged against those three properties.

| Service | Stack | Port | Responsibility |
|---|---|---|---|
| `market-data-service` | Java 21, Spring Boot 3, Flyway, ta4j | 8081 | OHLCV ingestion, technical indicators, time-series APIs |
| `trading-core-service` | Java 21, Spring Boot 3, Spring Security, JWT | 8082 | Identity, signals, strategies, subscriptions, backtesting |
| `ai-engine` | Python 3.11, FastAPI, PyTorch, pandas | 8000 | CNN training and inference, model registry |
| `web-app` | Next.js 14 App Router, TypeScript, Tailwind, shadcn/ui | 3000 | Operator and customer dashboard |

Cross-cutting: `shared/api-specs/` hold OpenAPI contracts. Single source of truth for inter-service shapes — generate clients, no hand-written DTOs that drift.

---

## 2. Operating Principles

1. **Read before write.** Inspect file, surrounding module, covering test. Never change function whose tests not opened.
2. **Smallest reversible step.** One cohesive commit over sweeping refactor. Task fans beyond two files → stop, surface plan.
3. **Boundary respect.** Cross-service contracts change only via `shared/api-specs/` + paired migration of every consumer. Never let two services drift on same DTO.
4. **Stateless edges.** Controllers, route handlers, React Server Components free of mutable module-level state. Persistence belong in repositories, caches, message bus.
5. **Determinism in core.** Signal generation, backtests, indicators must be pure: same input, same output, same ordering, no wall-clock reads. Inject `Clock` (Java), `datetime` providers (Python), or pass `now` from caller.
6. **Auditability.** Every state-changing op emit structured log with `correlation_id`, `user_id` (when present), `service`, `event`. No silent mutations.
7. **Fail loud at boundaries, fail safe in core.** Validate at edge (HTTP, queue consumers, CLI). Inside core, trust invariants — defensive coding hide bugs.

---

## 3. Local Environment

### JDK and Maven

- **Prefer repo wrapper** when present: `./mvnw` (Linux/macOS) or `mvnw.cmd` (Windows). Fall back to global Maven only if wrapper absent or non-executable.
- **Fallback Maven**: `C:\Users\JFERNANDEZ\tools\apache-maven-3.9.10\bin\mvn.cmd`
- **JDK 21**: `C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot`
- Activate Maven for current PowerShell session before tests:

  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
  $env:Path = "C:\Users\JFERNANDEZ\tools\apache-maven-3.9.10\bin;$env:JAVA_HOME\bin;$env:Path"
  ```

### Common workflows

```bash
make infra-up          # PostgreSQL + Redis + RabbitMQ
make up                # full stack
make test              # all suites
make test-web-app      # one service
make logs-trading-core-service
```

Service-local commands live in `AGENTS.md` and each service `README.md`. Read before inventing own.

---

## 4. Coding Conventions

| Language | Style | Lint / Format | Notes |
|---|---|---|---|
| Java 21 | 4-space indent, `PascalCase` classes, `camelCase` methods | Spotless (if configured), Checkstyle | Records for value types. Constructor injection only — no field `@Autowired`. |
| Python 3.11 | PEP 8, line length 100 | Black, Ruff, MyPy strict | `snake_case`, type hints required on public APIs. |
| TypeScript | 2-space indent, double quotes | ESLint, Prettier | `PascalCase` components, `camelCase` utilities, no default exports for components. |
| SQL | Lowercase keywords tolerated; uppercase preferred for new code | Flyway versioned migrations | One change per migration. Never edit shipped migration. |

**Naming discipline**: identifiers carry intent. `userRepo` over `repo`, `signalPublisher` over `publisher`. Avoid one-letter names outside loop bodies.

**Comments**: explain *why*, not *what*. The `what` is code's job.

---

## 5. Testing Doctrine

- **Unit tests** = default. Prove unit's contract in milliseconds.
- **Integration tests** prove wiring. Use Testcontainers (Java), `pytest-docker` or fixtures with real PG (Python), MSW or Playwright (web).
- **Never mock DB in integration tests.** Mocked DBs hide migration bugs. Hit real PG container.
- **Determinism**: tests depending on `now()` must inject clock. Flaky tests = bugs — quarantine, never `@Disabled`.
- **Coverage gate**: aim ≥ 80 % line coverage on changed files. Coverage = floor, not goal.
- **Test names describe behavior**: `rejectsExpiredJwtOnAdminRoute`, not `testAuth1`.

Service-specific layout:

- Java: `src/test/java`, classes ending `*Test.java` (unit) or `*IT.java` (integration, Maven Failsafe).
- Python: `tests/unit/` and `tests/integration/`, files named `test_*.py`.
- Web app: Vitest `*.test.ts` next to code; Playwright e2e under `tests/e2e/`.

---

## 6. Security Baseline

- `.env`, `*.pem`, `*.key`, `credentials*` = **read-denied** at harness level. No code that reads them outside explicit secret loader.
- Auth boundary: every `/api/v1/models/**` and `/api/v1/training/**` route returns `401` without valid admin JWT. Tests must cover unauthenticated path.
- All outbound HTTP from services route through configured client with timeouts and circuit-breaker policy. No raw `curl` from app code.
- SQL parameterized. String-concatenated SQL = defect, not style issue.
- Secrets in CI come from GitHub Actions secrets. Never echo, never log, never paste in PR descriptions.

See `.claude/agents/security-auditor.md` for deep checklist.

---

## 7. Git and PR Discipline

- **Commit message format**: Conventional Commits.
  `feat(trading-core): add JWT refresh endpoint`,
  `fix(ai-engine): clamp inference batch size to model capacity`,
  `chore(infra): bump postgres to 16.4`.
- **One concern per commit.** Reformatting + behavior change in one commit = review tax; split.
- **PR checklist**:
  - [ ] Tests added or updated, fail without change.
  - [ ] Migrations forward-only, reversible if data-bearing.
  - [ ] OpenAPI specs regenerated when contracts change.
  - [ ] Logs/metrics added for new state transitions.
  - [ ] Screenshots for UI changes.
  - [ ] Linked planning artifact (PBI, issue) when exists.
- **Never** force-push to `main` or any release branch. Force-push to own feature branch only when nobody else collaborating.
- Hooks (`--no-verify`, `--no-gpg-sign`) off limits unless user asks by name.

---

## 8. Observability

Every service emits:

- Structured JSON logs to stdout (Logback JSON encoder for Java, `structlog` for Python, `pino` for Node).
- Prometheus metrics on `/actuator/prometheus` (Java) and `/metrics` (Python/Node).
- OpenTelemetry traces propagated via `traceparent` header.

Add metric or span when add state transition. Cannot measure → cannot operate.

---

## 9. When in Doubt

Order of consultation:

1. Relevant `.claude/rules/*.md` (frontend, api, database).
2. Service's `README.md` and `AGENTS.md`.
3. `PLAN_EXECUTION.md` for current PBI scope.
4. Ask user. Asking cheaper than wrong refactor.

---

## 10. CI/CD Workflows (`.github/workflows/`)

Authoritative inventory as of 2026-05-07. Contract: PR breaking any required workflow = no merge. Add or modify pipeline → update this section in same commit.

### Release orchestration model

Every push to `main` is owned by `release-orchestrator.yml`. The orchestrator fans out the four reusable CI workflows in parallel for the same SHA, then runs the reusable staging deploy, then the reusable production deploy via the `production-auto` GitHub environment (no required reviewers). Per-service CI wrappers handle PRs and `develop` pushes only — they do not auto-fire on `main` push (the orchestrator does that work).

| Layer | Files |
|---|---|
| Wrappers (PR/develop/manual) | `ci-market-data-service.yml`, `ci-trading-core-service.yml`, `ci-ai-engine.yml`, `ci-web-app.yml` |
| Reusable CI bodies | `ci-<service>-reusable.yml` × 4. Inputs: `ref`, `push_image`. |
| Reusable deploys | `deploy-staging-reusable.yml` (input: `sha`), `deploy-production-reusable.yml` (inputs: `sha`, `environment`) |
| Manual deploy entrypoints | `deploy-staging.yml` (manual-only), `deploy-production.yml` (manual-only, `confirm == "deploy"`) |
| Release entrypoint | `release-orchestrator.yml` (push to `main` + `workflow_dispatch`) |

The `production` environment retains required reviewers and is used only by `deploy-production.yml`. The `production-auto` environment exists with the same secrets but no reviewers and is used only by the orchestrator.

### Per-service CI (path-filtered, runs on push/PR to `main`/`develop`)

| File | Service | Stack | Path filter | Notes |
|---|---|---|---|---|
| `ci-market-data-service.yml` | market-data-service | JDK 21 (Temurin), Maven cache | `services/market-data-service/**` | `mvn clean verify -q`. Self-contained; no DB sidecar. |
| `ci-trading-core-service.yml` | trading-core-service | JDK 21 (Temurin), Maven cache | `services/trading-core-service/**` | Spins `postgres:16-alpine` service container (`trading_core_test`/`test`/`test`) for integration tests. |
| `ci-ai-engine.yml` | ai-engine | Python 3.11 | `services/ai-engine/**` | Spins `postgres:16-alpine` (`ai_engine_test`/`test`/`test`) sidecar. Lint + unit + integration. |
| `ci-web-app.yml` | web-app | Node.js 20, npm cache | `services/web-app/**` | `npm ci` + lint + build + Vitest. |

All four also fire on `workflow_dispatch`. Path filters mean docs-only PR no trigger CI; no bypass by editing unrelated files.

### Deployment

| File | Trigger | Behavior |
|---|---|---|
| `release-orchestrator.yml` | push to `main` under `services/**`, `infrastructure/**`, `.github/workflows/**`, or `workflow_dispatch` (optional `sha`) | Owns automated `main` releases. Fans out 4 reusable CIs → reusable staging deploy → reusable production deploy (`production-auto` env). Concurrency group `release-main`, no cancel-in-progress. |
| `deploy-staging.yml` | `workflow_dispatch` only (optional `sha`) | Manual staging deploy entrypoint. Resolves SHA against last 30 commits if no `sha` provided, then calls `deploy-staging-reusable.yml`. |
| `deploy-production.yml` | `workflow_dispatch` only, **requires `confirm == "deploy"`**, optional `sha` | Manual production entrypoint. Calls `deploy-production-reusable.yml` with `environment: production` (preserves required-reviewer gate). |
| `deploy-staging-reusable.yml` | reusable (`workflow_call`) | Input: `sha`. Concurrency group `deploy-staging`. Verifies all 4 GHCR images exist for SHA, runs k8s rolling deploy, runs smoke tests. |
| `deploy-production-reusable.yml` | reusable (`workflow_call`) | Inputs: `sha`, `environment`. Concurrency group `deploy-production`. Verifies images, rolling deploy, prod smoke tests, rollback on failure, tags release on success. |

**Production rule**: never invoke `deploy-production.yml` from script. Operator types `deploy` into confirmation input. The orchestrator's automated path uses `production-auto` and is allowed.

### Security and supply chain

| File | Trigger | Scope |
|---|---|---|
| `trivy-scan.yml` | reusable (`workflow_call`) | Inputs: `image-ref`, `dockerfile`, `context`. Builds image, scans with Trivy, uploads SARIF to GitHub Security. Returns `result` output. |
| `scan-images.yml` | push/PR to `main`/`develop`, weekly cron (Mon 06:00 UTC), `workflow_dispatch` | Fans out to `trivy-scan.yml` per service (market-data, trading-core, ai-engine, web-app). Surfaces CVEs in deployed images. |
| `owasp-dependency-check.yml` | weekly cron (Mon 03:00 UTC), `workflow_dispatch` | OWASP Dependency-Check across services. Cached NVD DB. Retries up to 3× on transient NVD failures. |

`.trivyignore` at repo root silences accepted findings; review entries on every dependency bump.

### Operational

| File | Trigger | Purpose |
|---|---|---|
| `load-tests.yml` | `workflow_dispatch` (inputs: `base_url`, `users`, `duration`) | Runs k6 scenarios (`login-flow.js`, `fetch-signals.js`, `run-backtest.js`) via Docker. Default base URL local; set explicitly for staging. |
| `seed-market-data.yml` | `workflow_dispatch` only | **Self-hosted runner.** Port-forwards `svc/postgres` from `trading-saas` namespace, runs `scripts/seed_market_data.py` (yfinance + psycopg2), kills port-forward. |
| `opencode.yml` | `issue_comment`, `pull_request_review_comment` (commands `/oc`, `/opencode`), `workflow_dispatch` | OpenCode review/fix/SonarQube triage bot. Permissions: `id-token: write`, `contents: write`, `pull-requests: write`, `issues: write`, `actions: read`, `checks: read`. |

### Required-vs-optional matrix

| Workflow | Required for merge to `main` | Notes |
|---|---|---|
| `release-orchestrator.yml` | Yes (jobs `ci-market-data-service`, `ci-trading-core-service`, `ci-ai-engine`, `ci-web-app`) | Branch protection must require these orchestrator jobs as status checks. |
| `ci-*.yml` (wrapper) | PR-scoped (each wrapper required when its path filter matches) | Wrappers do not run on `main` push; orchestrator owns that. |
| `scan-images.yml` | Weekly cron + PR | Per-service CI's `security-scan` step covers `main` SHA scans; `scan-images.yml` runs on schedule and PRs. |
| `trivy-scan.yml` | Indirect (called by reusable CI and `scan-images.yml`) | — |
| `owasp-dependency-check.yml` | No (scheduled audit) | New `CRITICAL` ⇒ open follow-up issue. |
| `deploy-staging.yml` | No (manual entrypoint) | Orchestrator handles `main`; wrapper for ad-hoc redeploys. |
| `deploy-production.yml` | No (manual gate, required reviewers) | Human-confirmed only via `production` environment. |
| `load-tests.yml` | No | Run before major releases or perf-sensitive merges. |
| `seed-market-data.yml` | No | Operator-only, self-hosted. |
| `opencode.yml` | No | Convenience bot. |

### Conventions for editing workflows

- **Pin third-party actions by full commit SHA**, not tag (`@v4` → `@<40-char-sha> # v4.x`). Tags mutable.
- **Permissions block** at job level, minimal. Default `contents: read` plus only what job needs.
- **Concurrency groups** on any deploy or release workflow to prevent overlap.
- **Secrets** never echoed in `run:` blocks; never written to artifacts or logs.
- **Path filters** stay aligned with service directories — adding top-level shared file consumed by service requires extending filters, not removing.
- **`workflow_dispatch`** mandatory on every CI workflow so operators replay against specific SHA.
- **Self-hosted runners** (`seed-market-data.yml`) require kubeconfig + port-forward access. Never schedule untrusted code on them.

### Operator quick reference

```bash
# Watch latest runs on current branch
gh run list --branch "$(git rev-parse --abbrev-ref HEAD)" --limit 5
gh run view <id> --log
gh run view <id> --log-failed

# Re-run failed jobs
gh run rerun <id> --failed

# Trigger a manual workflow
gh workflow run release-orchestrator.yml                    # release HEAD of main
gh workflow run release-orchestrator.yml -f sha=<short-sha>  # release specific SHA
gh workflow run deploy-staging.yml -f sha=<short-sha>
gh workflow run deploy-production.yml -f sha=<short-sha> -f confirm=deploy
gh workflow run load-tests.yml -f base_url=https://staging.tradingsaas -f users=200 -f duration=5m
gh workflow run scan-images.yml
gh workflow run owasp-dependency-check.yml
```

---

## 11. Progress Tracking

`PLAN_EXECUTION.md` = backlog. After every completed PBI, append:

- last completed task (id + one-line summary)
- next task in development

Keep section short. Audit trail live in git, not this file.

---

## 12. Sprint Tracker — Track A (feature-enrichment-stock-detail)

| Sprint | PBI    | Status | Summary |
|--------|--------|--------|---------|
| A1     | TS-301 | Listo  | Enrichment domain models + ports |
| A2     | TS-302 | Listo  | FinnhubAdapter (WireMock 10 tests) + RedisEnrichmentCacheAdapter + application.yml |
| A3     | TS-303 | Listo  | Use cases + EnrichmentController (6 endpoints) |
| A4     | TS-304 | Listo  | trading-core proxy adapter + EnrichmentProxyController |
| A5     | TS-305 | Listo  | enrichment-client.ts + SSR helper + /api/stocks/[ticker] route |
| A6     | TS-306 | Listo  | TradingView AdvancedChart + MiniChart + CSP update |
| A7     | TS-307 | Listo  | Static components: CompanyHeader, FundamentalsPanel, EarningsBadge, AnalystRecommendationsBar, PeersList |
| A8     | TS-308 | Listo  | Dynamic: NewsFeed (infinite scroll) + AISignalSection + /news paginated route |
| A9     | TS-309 | Listo  | /dashboard/stocks/[ticker] page + loading.tsx + error.tsx |
| A10    | TS-310 | Listo  | Infra (docker-compose, k8s finnhub-credentials) + e2e + portfolio ticker links |

**Last Completed:** TS-310 — All Track A sprints complete. PR #190 opened to develop. 58 Vitest unit tests green. `npm run build` passes. `/dashboard/stocks/[ticker]` live as a dynamic SSR route.

**Next In Development:** Track A complete. PR #190 awaiting review at https://github.com/Jfdz/TradeMindAI/pull/190.

**Lesson (branch hygiene):** Always verify `git branch --show-current` before committing. Bash tool sessions don't inherit previous session's CWD state — git HEAD file is shared, so branch switches take effect globally. Never commit without confirming current branch.

**Key decisions:**
- EnrichmentCache is generic: `get(key, Class<T>)` / `put(key, T, Duration ttl)` — TTL comes from use-case layer per property.
- `CompanyProfile`: only `ticker` + `name` are non-null; all other fields nullable (Finnhub inconsistent).
- `NewsItem.id` is `long` (stable Finnhub integer) for React Query cursor pagination in A8.
- `Instant` for news/earnings timestamps (ISO-8601 via Spring).
- `GetCompanyNewsUseCase` has two methods: market news (category+limit) and ticker news (from+to+limit).
- `GetPeersUseCase` returns `List<String>` (no wrapper record).

---

## 13. Sprint Tracker — Track B (feature-signals-quality)

| Sprint | PBI    | Status         | Summary |
|--------|--------|----------------|---------|
| B1     | TS-311 | Listo          | G.1 Backend — entryPrice on signals |
| B2     | TS-312 | In Development | G.1 Frontend + G.2 Pagination |
| B3–B7  |        | To Do          | — |

**Last Completed:** TS-311 — V15 migration (`entry_price NUMERIC(18,6) NULL`), `TradingSignal` + `TradingSignalJpaEntity` + mapper updated, `SignalGenerationService` injects `HistoricalMarketDataPort` to capture price at generate(), `SignalController.SignalResponse` exposes `entryPrice`. 111 tests green, `mvn verify` BUILD SUCCESS.

**Next In Development:** TS-312 — G.1 Frontend (`entryPrice` in `SignalResponse` TS type + `signal-derivation.ts` fallback) + G.2 Pagination (`getSignals(opts)` with page/size/sort, `fetchSignalsPageData()` with pageInfo, `pagination-controls.tsx` server component, `signals/page.tsx` URL-driven pagination).

**Key decisions:**
- No cross-schema backfill in V15 (database.md §11 forbids cross-schema joins). Old rows stay NULL.
- Market-data fetch wrapped in try/catch: exception → NULL entryPrice, not signal failure.
- `TradingSignal` chain: 6-arg → 11-arg (all nullable extras), 8-arg → 11-arg, 9-arg → 11-arg, 10-arg → 11-arg. Preserves backward compat.
- Branch accidental checkout to `feature-enrichment-stock-detail`: cherry-picked `ae92578` to correct branch, reset Track A branch back to `932012b`.