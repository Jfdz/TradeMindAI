# Trading SaaS Platform - Architecture & Implementation Plan

## Context

Build a production-ready, subscription-based SaaS platform that uses AI (CNNs/deep learning) to analyze stock market data and generate actionable trading signals (buy/sell/hold). The platform must be scalable to thousands of users, secure by design, and monetizable with free/premium tiers. Starting from an empty workspace at `c:\Users\fakdu\workspace\projects\trading_saas`.

---

## Architecture Overview

```mermaid
graph TB
    subgraph Frontend
        WEB["web-app<br/><i>Next.js 14 :3000</i>"]
    end

    subgraph Backend Services
        TC["trading-core-service<br/><i>Spring Boot :8082</i>"]
        MD["market-data-service<br/><i>Spring Boot :8081</i>"]
    end

    subgraph AI
        AIE["ai-engine<br/><i>FastAPI + PyTorch :8000</i>"]
    end

    subgraph Infrastructure
        PG[("PostgreSQL 16<br/><i>3 schemas</i>")]
        RD[("Redis 7<br/><i>Cache + JWT blacklist</i>")]
        RMQ[("RabbitMQ 3.13<br/><i>Async messaging</i>")]
        MS[("Model Store<br/><i>Filesystem / S3</i>")]
    end

    WEB -- "REST + JWT" --> TC
    WEB -- "REST (public)" --> MD
    TC -- "REST (sync inference)" --> AIE
    MD -. "RabbitMQ<br/>market-data.prices.updated" .-> AIE
    AIE -. "RabbitMQ<br/>prediction.result.completed" .-> TC

    TC --> PG
    MD --> PG
    AIE --> PG
    TC --> RD
    MD --> RD
    TC --> RMQ
    MD --> RMQ
    AIE --> RMQ
    AIE --> MS

    style WEB fill:#3b82f6,stroke:#2563eb,color:#ffffff,stroke-width:2px
    style TC fill:#10b981,stroke:#059669,color:#ffffff,stroke-width:2px
    style MD fill:#10b981,stroke:#059669,color:#ffffff,stroke-width:2px
    style AIE fill:#8b5cf6,stroke:#7c3aed,color:#ffffff,stroke-width:2px
    style PG fill:#f59e0b,stroke:#d97706,color:#1f2937,stroke-width:2px
    style RD fill:#ef4444,stroke:#dc2626,color:#ffffff,stroke-width:2px
    style RMQ fill:#f97316,stroke:#ea580c,color:#ffffff,stroke-width:2px
    style MS fill:#6b7280,stroke:#4b5563,color:#ffffff,stroke-width:2px
```

> **Legend:**
> $\color{#3b82f6}{\textsf{Blue}}$ = Frontend |
> $\color{#10b981}{\textsf{Green}}$ = Java Backend |
> $\color{#8b5cf6}{\textsf{Purple}}$ = AI / Python |
> $\color{#f59e0b}{\textsf{Amber}}$ = Database |
> $\color{#ef4444}{\textsf{Red}}$ = Cache |
> $\color{#f97316}{\textsf{Orange}}$ = Messaging

### 4 Services, Clear Boundaries

| Service | Stack | Responsibility |
|---|---|---|
| `market-data-service` | Java 21 / Spring Boot 3 | Data ingestion (Yahoo Finance), OHLCV storage, technical indicators (RSI, MACD, SMA via ta4j), historical data API |
| `trading-core-service` | Java 21 / Spring Boot 3 | Auth (JWT), subscriptions, signal generation, strategies, backtesting, portfolios, risk management |
| `ai-engine` | Python / FastAPI / PyTorch | CNN model training & inference, feature engineering, model versioning |
| `web-app` | TypeScript / Next.js 14 | Dashboard UI, charts (TradingView Lightweight Charts), auth flows, SSR landing pages |

### Key Architectural Decisions

- **Monorepo**: Single repo, path-triggered CI per service. Simpler for small teams.
- **Shared PostgreSQL, separate schemas**: `market_data`, `trading_core`, `ai_engine` - logical isolation, operational simplicity. No cross-schema joins.
- **Hybrid communication**: REST for sync inference, RabbitMQ for async batch predictions and event-driven flows.
- **RabbitMQ over Kafka**: Simpler ops for thousands (not millions) of users. Built-in DLQ/retry.
- **Clean Architecture in Java**: `domain/` (pure Java) -> `application/` (use cases) -> `adapter/` (Spring, JPA, external). Enforced by ArchUnit tests.

---

## Clean Architecture (Java Services)

```mermaid
graph LR
    subgraph outer["Adapter Layer (outer)"]
        WEB_CTRL["Web Controllers<br/><i>Spring MVC</i>"]
        JPA_REPO["JPA Repositories<br/><i>Hibernate</i>"]
        EXT["External Clients<br/><i>WebClient, RabbitMQ</i>"]
        SCHED["Schedulers<br/><i>@Scheduled</i>"]
    end

    subgraph mid["Application Layer"]
        UC["Use Case Impls"]
    end

    subgraph inner["Domain Layer (inner, pure Java)"]
        MODEL["Entities &<br/>Value Objects"]
        PORTS_IN["Input Ports<br/><i>interfaces</i>"]
        PORTS_OUT["Output Ports<br/><i>interfaces</i>"]
        SVC["Domain Services"]
    end

    WEB_CTRL --> PORTS_IN
    SCHED --> PORTS_IN
    PORTS_IN --> UC
    UC --> SVC
    UC --> PORTS_OUT
    JPA_REPO -.->|implements| PORTS_OUT
    EXT -.->|implements| PORTS_OUT
    SVC --> MODEL

    style WEB_CTRL fill:#60a5fa,stroke:#3b82f6,color:#1e3a5f
    style JPA_REPO fill:#60a5fa,stroke:#3b82f6,color:#1e3a5f
    style EXT fill:#60a5fa,stroke:#3b82f6,color:#1e3a5f
    style SCHED fill:#60a5fa,stroke:#3b82f6,color:#1e3a5f
    style UC fill:#fbbf24,stroke:#f59e0b,color:#1f2937
    style MODEL fill:#34d399,stroke:#10b981,color:#064e3b
    style PORTS_IN fill:#34d399,stroke:#10b981,color:#064e3b
    style PORTS_OUT fill:#34d399,stroke:#10b981,color:#064e3b
    style SVC fill:#34d399,stroke:#10b981,color:#064e3b
```

> **Legend:**
> $\color{#34d399}{\textsf{Green}}$ = Domain (pure Java, no frameworks) |
> $\color{#fbbf24}{\textsf{Yellow}}$ = Application (orchestration) |
> $\color{#60a5fa}{\textsf{Blue}}$ = Adapters (Spring, JPA, external)

---

## Monorepo Structure

```
trading_saas/
+-- README.md
+-- docker-compose.yml
+-- docker-compose.prod.yml
+-- Makefile
+-- .gitignore
+-- .env.example
+-- .github/workflows/          (CI per service, path-triggered)
+-- infrastructure/
|   +-- k8s/                    (Kubernetes manifests per service)
|   +-- init-schemas.sql        (CREATE SCHEMA for all 3 schemas)
+-- services/
|   +-- market-data-service/    (Java 21, Spring Boot 3, Maven)
|   +-- trading-core-service/   (Java 21, Spring Boot 3, Maven)
|   +-- ai-engine/              (Python 3.11, FastAPI, PyTorch)
|   +-- web-app/                (Next.js 14, TypeScript, Tailwind)
+-- shared/
|   +-- api-specs/              (OpenAPI 3.1 specs)
+-- scripts/
    +-- setup-dev.sh
    +-- seed-data.sh
```

---

## Phased Implementation Plan

```mermaid
gantt
    title Implementation Phases
    dateFormat YYYY-MM-DD
    axisFormat %b %d

    section Phase 1 - Foundation
    Monorepo + Docker infra          :done, p1a, 2026-03-30, 7d
    market-data-service              :active, p1b, after p1a, 14d

    section Phase 2 - AI
    CNN model + training             :p2a, after p1b, 10d
    FastAPI + RabbitMQ integration   :p2b, after p2a, 11d

    section Phase 3 - Core
    Auth + JWT + Subscriptions       :p3a, after p2b, 10d
    Signals + Strategies + Risk      :p3b, after p3a, 18d

    section Phase 4 - Frontend
    Next.js scaffold + Auth UI       :p4a, after p3b, 10d
    Dashboard + Charts               :p4b, after p4a, 18d

    section Phase 5 - Backtest
    Backtesting engine               :p5a, after p4b, 14d
    Frontend backtest UI             :p5b, after p5a, 14d

    section Phase 6 - Prod
    Docker + K8s + CI/CD             :p6a, after p5b, 14d
    Observability + Security         :p6b, after p6a, 14d
```

### Phase 1: Foundation - Monorepo + Data Ingestion
**Goal**: Working monorepo, infrastructure, market data service with Yahoo Finance ingestion

**Steps**:
1. Initialize git repo, create monorepo structure, `.gitignore`, `Makefile`
2. Create `docker-compose.yml` with PostgreSQL 16, Redis 7, RabbitMQ 3.13
3. Create `infrastructure/init-schemas.sql` (market_data, trading_core, ai_engine schemas)
4. Scaffold `market-data-service` (Spring Boot 3, Maven, Java 21):
   - Clean Architecture packages: `domain/`, `application/`, `adapter/`, `config/`
   - Domain models: `StockPrice`, `Symbol`, `TechnicalIndicator`, `OHLCV`, `TimeFrame`
   - Ports (interfaces): `MarketDataProvider`, `StockPriceRepository`, `MarketDataEventPublisher`
   - Use cases: `FetchMarketDataUseCase`, `GetHistoricalDataUseCase`, `CalculateIndicatorsUseCase`
   - Adapters: Yahoo Finance REST adapter, JPA repository, RabbitMQ publisher
   - Scheduled ingestion job (configurable symbols, daily cron)
   - Technical indicators via `ta4j` library (RSI, MACD, SMA-20, SMA-50)
   - REST API: `GET /api/v1/symbols`, `GET /api/v1/prices/{ticker}/history`, `GET /api/v1/indicators/{ticker}`
   - Flyway migrations: `symbols`, `stock_prices`, `technical_indicators` tables
   - Redis caching for latest prices
   - Health check via Spring Actuator
5. Unit tests (domain logic, indicator calculations) + integration test (data persistence)
6. Dockerfile (multi-stage build)

**Key files to create**:
- `services/market-data-service/pom.xml`
- `services/market-data-service/src/main/java/com/tradingsaas/marketdata/` (full Clean Arch tree)
- `services/market-data-service/src/main/resources/application.yml`
- `services/market-data-service/src/main/resources/db/migration/V1__*.sql, V2__*.sql`
- `services/market-data-service/Dockerfile`

**Libraries**: Spring Boot 3.3, Spring Data JPA, Flyway, ta4j 0.16, Spring AMQP, Spring Data Redis

---

### Phase 2: AI Engine - CNN Model + Prediction API
**Goal**: Working CNN model, FastAPI prediction endpoint, RabbitMQ integration

```mermaid
graph LR
    IN["Input<br/><b>(batch, 17, 60)</b>"]
    C1["Conv1d 17→64, k=3<br/>BatchNorm → ReLU<br/>MaxPool(2)"]
    C2["Conv1d 64→128, k=3<br/>BatchNorm → ReLU<br/>MaxPool(2)"]
    C3["Conv1d 128→256, k=3<br/>BatchNorm → ReLU<br/>AdaptiveAvgPool(1)"]
    FC1["Linear 256→128<br/>Dropout 0.5"]
    FC2["Linear 128→3"]
    OUT["Output<br/><b>DOWN | NEUTRAL | UP</b>"]

    IN --> C1 --> C2 --> C3 --> FC1 --> FC2 --> OUT

    style IN fill:#e0e7ff,stroke:#6366f1,color:#312e81,stroke-width:2px
    style C1 fill:#8b5cf6,stroke:#7c3aed,color:#ffffff,stroke-width:2px
    style C2 fill:#7c3aed,stroke:#6d28d9,color:#ffffff,stroke-width:2px
    style C3 fill:#6d28d9,stroke:#5b21b6,color:#ffffff,stroke-width:2px
    style FC1 fill:#f59e0b,stroke:#d97706,color:#1f2937,stroke-width:2px
    style FC2 fill:#f59e0b,stroke:#d97706,color:#1f2937,stroke-width:2px
    style OUT fill:#10b981,stroke:#059669,color:#ffffff,stroke-width:2px
```

> **Legend:**
> $\color{#e0e7ff}{\textsf{Indigo light}}$ = Input |
> $\color{#8b5cf6}{\textsf{Purple gradient}}$ = Conv blocks (deeper = later) |
> $\color{#f59e0b}{\textsf{Amber}}$ = Fully connected |
> $\color{#10b981}{\textsf{Green}}$ = Output

**Steps**:
1. Scaffold `ai-engine` Python service with `pyproject.toml` and `requirements.txt`
2. FastAPI application with health, readiness, and prediction endpoints
3. CNN model (PyTorch): 3-block 1D CNN as shown above
4. Feature engineering pipeline:
   - 17 features: OHLCV (5) + RSI, MACD, MACD_signal, MACD_hist, SMA_20, SMA_50, EMA_12, EMA_26, BB_upper, BB_lower, ATR, OBV (12)
   - MinMaxScaler normalization, sliding window sequence builder
5. Training pipeline: DataLoader, CrossEntropyLoss with class weights, AdamW optimizer, ReduceLROnPlateau, early stopping
6. Label generation: compare close[t+5] vs close[t] -> UP (>+1%), DOWN (<-1%), NEUTRAL
7. Model registry: version tracking, artifact storage, active model selection
8. Alembic migrations for `ai_engine` schema (model_versions, training_runs, predictions)
9. RabbitMQ consumer for batch prediction requests, publisher for results
10. REST endpoints: `POST /api/v1/predict`, `POST /api/v1/predict/batch`, `POST /api/v1/models/train`, `GET /api/v1/models`
11. Abstract `BasePredictor` interface for future LSTM/Transformer extensibility
12. Unit tests (model shape, feature engineering) + integration tests (API)
13. Dockerfile

**Key files**:
- `services/ai-engine/pyproject.toml`, `requirements.txt`
- `services/ai-engine/src/ai_engine/main.py`
- `services/ai-engine/src/ai_engine/core/models/base.py`, `cnn.py`
- `services/ai-engine/src/ai_engine/core/preprocessing/feature_engineering.py`, `normalizer.py`, `sequence_builder.py`
- `services/ai-engine/src/ai_engine/core/training/trainer.py`, `evaluator.py`
- `services/ai-engine/src/ai_engine/core/inference/predictor.py`, `model_registry.py`
- `services/ai-engine/src/ai_engine/api/routes/prediction.py`, `model.py`, `health.py`
- `services/ai-engine/src/ai_engine/messaging/consumer.py`, `publisher.py`

**Libraries**: FastAPI, PyTorch, pandas, scikit-learn, ta (technical analysis), aio-pika, SQLAlchemy, Alembic

---

### Phase 3: Trading Core - Auth, Signals, Strategies
**Goal**: User system with JWT, signal generation from AI predictions, strategy & risk management

```mermaid
sequenceDiagram
    box rgb(219, 234, 254) User
        participant U as User
    end
    box rgb(209, 250, 229) Java Backend
        participant TC as trading-core
        participant MD as market-data
    end
    box rgb(237, 233, 254) AI
        participant AI as ai-engine
    end
    box rgb(255, 237, 213) Messaging
        participant RMQ as RabbitMQ
    end

    U->>+TC: POST /auth/login
    TC-->>-U: JWT (access + refresh)

    rect rgb(254, 249, 195)
        Note over MD,RMQ: Daily ingestion trigger (6pm EST)
        MD->>RMQ: market-data.prices.updated
        RMQ->>AI: consume event
        AI->>AI: Feature engineering + CNN inference
        AI->>RMQ: prediction.result.completed
        RMQ->>TC: consume prediction
        TC->>TC: Apply strategy rules + risk mgmt
        TC->>TC: Store TradingSignal
    end

    U->>+TC: GET /signals (Bearer JWT)
    TC-->>-U: [{ type: BUY, confidence: 0.82, ... }]
```

**Steps**:
1. Scaffold `trading-core-service` (Spring Boot 3, Maven):
   - DDD bounded contexts: `user/`, `signal/`, `strategy/`, `portfolio/` (package-level)
   - Clean Architecture within each context
2. User domain:
   - Entities: `User`, `Subscription` (plan: FREE/BASIC/PREMIUM)
   - Spring Security 6 + custom JWT filter (access token 15min, refresh token 7d)
   - BCrypt password hashing, refresh token rotation, Redis blacklist for logout
   - REST: `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`
3. Signal domain:
   - Entity: `TradingSignal` (type: BUY/SELL/HOLD, confidence 0-1, timeframe)
   - `SignalGenerationService`: calls AI engine REST -> applies strategy rules -> stores signal
   - REST client to ai-engine with Resilience4j circuit breaker (timeout 5s, 50% failure threshold)
   - REST: `GET /signals`, `GET /signals/latest`, `GET /signals/{id}`
4. Strategy domain:
   - Entity: `Strategy` with `RiskParameters` (stop-loss %, take-profit %, max position %)
   - `RiskManager`: position sizing, stop-loss/take-profit calculation
   - CRUD REST: `POST/GET/PUT/DELETE /strategies`
5. Subscription enforcement:
   - Custom `@RequiresSubscription` annotation + AOP aspect
   - Rate limiting per tier via bucket4j + Redis (FREE: 5 signals/day, BASIC: 50, PREMIUM: unlimited)
6. RabbitMQ listener for prediction results -> auto-generate signals
7. Inter-service REST client to market-data-service for price data
8. Flyway migrations: users, subscriptions, trading_signals, strategies, portfolios, positions, backtests
9. Comprehensive tests: security tests, controller tests (MockMvc), use case tests, ArchUnit tests
10. Dockerfile

**Key files**:
- `services/trading-core-service/pom.xml`
- `services/trading-core-service/src/main/java/com/tradingsaas/tradingcore/` (DDD + Clean Arch tree)
- Flyway migrations V1-V6

**Libraries**: Spring Security 6, jjwt 0.12.5, Resilience4j, bucket4j, MapStruct

---

### Phase 4: Frontend Dashboard
**Goal**: Production-ready Next.js dashboard with auth, signals, charts

```mermaid
graph TB
    subgraph pages["Next.js App Router"]
        LAND["/ <br/><i>Landing SSR</i>"]
        PRICE["/pricing<br/><i>SSR</i>"]
        LOGIN["/auth/login"]
        REG["/auth/register"]

        subgraph dash["Dashboard (authenticated)"]
            DASH["/dashboard<br/><i>Overview</i>"]
            SIG["/dashboard/signals<br/><i>Signal table + charts</i>"]
            PORT["/dashboard/portfolio<br/><i>Positions, P&L</i>"]
            BT["/dashboard/backtest<br/><i>Config + results</i>"]
            SETT["/dashboard/settings<br/><i>Profile, subscription</i>"]
        end
    end

    subgraph libs["Client Libraries"]
        TQ["TanStack Query<br/><i>Server state</i>"]
        ZU["Zustand<br/><i>Client state</i>"]
        LWC["Lightweight Charts<br/><i>TradingView</i>"]
        SHAD["shadcn/ui<br/><i>Components</i>"]
    end

    DASH --> TQ
    SIG --> LWC
    SIG --> TQ
    PORT --> TQ
    BT --> TQ

    style LAND fill:#dbeafe,stroke:#3b82f6,color:#1e3a5f,stroke-width:2px
    style PRICE fill:#dbeafe,stroke:#3b82f6,color:#1e3a5f,stroke-width:2px
    style LOGIN fill:#fef3c7,stroke:#f59e0b,color:#78350f,stroke-width:2px
    style REG fill:#fef3c7,stroke:#f59e0b,color:#78350f,stroke-width:2px
    style DASH fill:#d1fae5,stroke:#10b981,color:#064e3b,stroke-width:2px
    style SIG fill:#d1fae5,stroke:#10b981,color:#064e3b,stroke-width:2px
    style PORT fill:#d1fae5,stroke:#10b981,color:#064e3b,stroke-width:2px
    style BT fill:#d1fae5,stroke:#10b981,color:#064e3b,stroke-width:2px
    style SETT fill:#d1fae5,stroke:#10b981,color:#064e3b,stroke-width:2px
    style TQ fill:#ede9fe,stroke:#8b5cf6,color:#4c1d95,stroke-width:2px
    style ZU fill:#ede9fe,stroke:#8b5cf6,color:#4c1d95,stroke-width:2px
    style LWC fill:#ede9fe,stroke:#8b5cf6,color:#4c1d95,stroke-width:2px
    style SHAD fill:#ede9fe,stroke:#8b5cf6,color:#4c1d95,stroke-width:2px
```

> **Legend:**
> $\color{#3b82f6}{\textsf{Blue}}$ = Public SSR pages |
> $\color{#f59e0b}{\textsf{Amber}}$ = Auth pages |
> $\color{#10b981}{\textsf{Green}}$ = Protected dashboard |
> $\color{#8b5cf6}{\textsf{Purple}}$ = Client libraries

**Steps**:
1. Scaffold `web-app` with Next.js 14 App Router, TypeScript, Tailwind CSS
2. Install shadcn/ui components, TradingView Lightweight Charts, TanStack Query, Zustand
3. SSR pages: Landing (`/`), Pricing (`/pricing`)
4. Auth pages: Login, Register (NextAuth.js with credentials provider -> trading-core JWT)
5. Dashboard layout: Sidebar nav, Header with user menu
6. Dashboard pages:
   - Overview: portfolio summary cards, top signals, recent activity
   - Signals (`/dashboard/signals`): filterable/sortable table, signal detail with candlestick chart
   - Portfolio (`/dashboard/portfolio`): positions, P&L, allocation donut chart
   - Backtest (`/dashboard/backtest`): config form, results with equity curve
   - Settings (`/dashboard/settings`): profile, subscription management
7. Charts: CandlestickChart with indicator overlays, PerformanceChart, DrawdownChart
8. API client layer with TanStack Query for caching
9. WebSocket/SSE for real-time signal updates (premium tier)
10. Responsive + dark mode
11. Dockerfile

**Key files**:
- `services/web-app/package.json`, `next.config.js`, `tailwind.config.ts`
- `services/web-app/src/app/` (all page routes)
- `services/web-app/src/components/` (UI, charts, signals, portfolio, layout)
- `services/web-app/src/lib/api-client.ts`, `auth.ts`
- `services/web-app/src/hooks/useSignals.ts`, `usePortfolio.ts`

**Libraries**: Next.js 14, shadcn/ui, TanStack Query v5, Zustand, Lightweight Charts, NextAuth.js

---

### Phase 5: Backtesting Engine + Advanced Features
**Goal**: Event-driven backtesting, performance analytics, portfolio tracking

```mermaid
graph LR
    DF["DataFeed<br/><i>Replays OHLCV<br/>day-by-day</i>"]
    STR["Strategy<br/><i>Evaluates signals<br/>per bar</i>"]
    BRK["Broker<br/><i>Simulates execution<br/>+ slippage</i>"]
    PTF["Portfolio<br/><i>Tracks positions<br/>+ equity curve</i>"]
    RESULT["Results<br/><b>Sharpe · Sortino<br/>Max Drawdown<br/>Win Rate · PnL</b>"]

    DF -->|"bar data"| STR
    STR -->|"order"| BRK
    BRK -->|"fill"| PTF
    PTF -->|"metrics"| RESULT

    style DF fill:#dbeafe,stroke:#3b82f6,color:#1e3a5f,stroke-width:2px
    style STR fill:#fef3c7,stroke:#f59e0b,color:#78350f,stroke-width:2px
    style BRK fill:#fce7f3,stroke:#ec4899,color:#831843,stroke-width:2px
    style PTF fill:#d1fae5,stroke:#10b981,color:#064e3b,stroke-width:2px
    style RESULT fill:#ede9fe,stroke:#8b5cf6,color:#4c1d95,stroke-width:2px
```

**Steps**:
1. Backtesting engine in `trading-core-service`:
   - Event-driven architecture: DataFeed -> Strategy -> Broker (simulated) -> Portfolio tracker
   - Slippage modeling, commission handling
   - Metrics: total return, annualized return, Sharpe ratio, Sortino ratio, max drawdown, Calmar ratio, win rate, profit factor
   - S&P 500 benchmark comparison
   - Async execution: submit backtest -> poll status -> fetch results
   - REST: `POST /backtests`, `GET /backtests`, `GET /backtests/{id}`, `GET /backtests/{id}/trades`
2. Portfolio tracking:
   - Position management (open/close)
   - P&L calculation (realized + unrealized)
   - Portfolio snapshots over time
3. Frontend additions:
   - Backtest configuration form (symbol, date range, strategy, initial capital)
   - Results: equity curve chart, drawdown chart, trade markers on price chart, metrics table
   - Strategy vs benchmark comparison view
4. Performance optimizations: Redis caching for hot data, DB query tuning, gzip compression

---

### Phase 6: Production Hardening + Deployment
**Goal**: Docker, K8s manifests, CI/CD, observability, security hardening

```mermaid
graph TB
    subgraph cicd["CI/CD Pipeline"]
        direction LR
        BUILD["Build"]
        TEST["Test"]
        DOCKER["Docker<br/>Build"]
        PUSH["Push<br/>Registry"]
        STAGING["Deploy<br/>Staging"]
        INTEG["Integration<br/>Tests"]
        PROD["Deploy<br/>Prod"]

        BUILD --> TEST --> DOCKER --> PUSH --> STAGING --> INTEG --> PROD
    end

    subgraph k8s["Kubernetes Cluster"]
        ING["Ingress<br/><i>HTTPS + CORS</i>"]
        WEB_D["web-app<br/><i>Deployment + HPA</i>"]
        TC_D["trading-core<br/><i>Deployment + HPA</i>"]
        MD_D["market-data<br/><i>Deployment + HPA</i>"]
        AI_D["ai-engine<br/><i>Deployment + HPA</i>"]

        ING --> WEB_D
        ING --> TC_D
        ING --> MD_D
        TC_D --> AI_D
    end

    subgraph obs["Observability"]
        PROM["Prometheus<br/><i>Metrics</i>"]
        GRAF["Grafana<br/><i>Dashboards</i>"]
        ZIP["Zipkin / Jaeger<br/><i>Tracing</i>"]
        ELK["ELK / CloudWatch<br/><i>Logs</i>"]

        PROM --> GRAF
    end

    TC_D -.-> PROM
    MD_D -.-> PROM
    AI_D -.-> PROM

    style BUILD fill:#dbeafe,stroke:#3b82f6,color:#1e3a5f
    style TEST fill:#dbeafe,stroke:#3b82f6,color:#1e3a5f
    style DOCKER fill:#dbeafe,stroke:#3b82f6,color:#1e3a5f
    style PUSH fill:#dbeafe,stroke:#3b82f6,color:#1e3a5f
    style STAGING fill:#fef3c7,stroke:#f59e0b,color:#78350f
    style INTEG fill:#fef3c7,stroke:#f59e0b,color:#78350f
    style PROD fill:#d1fae5,stroke:#10b981,color:#064e3b,stroke-width:3px
    style ING fill:#f3e8ff,stroke:#a855f7,color:#581c87,stroke-width:2px
    style WEB_D fill:#3b82f6,stroke:#2563eb,color:#ffffff
    style TC_D fill:#10b981,stroke:#059669,color:#ffffff
    style MD_D fill:#10b981,stroke:#059669,color:#ffffff
    style AI_D fill:#8b5cf6,stroke:#7c3aed,color:#ffffff
    style PROM fill:#fef3c7,stroke:#f59e0b,color:#78350f
    style GRAF fill:#d1fae5,stroke:#10b981,color:#064e3b
    style ZIP fill:#e0e7ff,stroke:#6366f1,color:#312e81
    style ELK fill:#e0e7ff,stroke:#6366f1,color:#312e81
```

**Steps**:
1. Multi-stage Dockerfiles for all services
2. `docker-compose.prod.yml` with resource limits, restart policies
3. Kubernetes manifests: Deployments, Services, Ingress, HPA, ConfigMaps, Secrets, NetworkPolicies
4. GitHub Actions CI/CD: build -> test -> Docker build -> push -> deploy staging -> integration tests -> deploy prod
5. Observability:
   - Structured JSON logging (logback for Java, structlog for Python)
   - Micrometer + Prometheus metrics + Grafana dashboards
   - Distributed tracing (Micrometer Tracing + Zipkin/Jaeger)
   - Liveness/readiness probes
6. Security hardening:
   - OWASP dependency check in CI
   - CSP headers, CORS config, HTTPS at ingress
   - Input validation (Bean Validation), SQL injection protection (JPA parameterized queries)
   - Secrets management (K8s Secrets / Vault)
   - Rate limiting at API gateway level
7. Load testing: k6 scripts for login, signals, backtest flows

---

## Database Schema Summary

```mermaid
erDiagram
    SYMBOLS {
        UUID id PK
        VARCHAR ticker UK
        VARCHAR name
        VARCHAR exchange
        VARCHAR sector
    }

    STOCK_PRICES {
        BIGSERIAL id PK
        UUID symbol_id FK
        DATE date
        VARCHAR timeframe
        NUMERIC open
        NUMERIC high
        NUMERIC low
        NUMERIC close
        NUMERIC adjusted_close
        BIGINT volume
    }

    TECHNICAL_INDICATORS {
        BIGSERIAL id PK
        UUID symbol_id FK
        DATE date
        VARCHAR indicator_type
        NUMERIC value
        JSONB metadata
    }

    USERS {
        UUID id PK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR first_name
        VARCHAR last_name
        BOOLEAN is_active
    }

    SUBSCRIPTIONS {
        UUID id PK
        UUID user_id FK
        VARCHAR plan
        VARCHAR status
        TIMESTAMPTZ expires_at
    }

    TRADING_SIGNALS {
        UUID id PK
        UUID symbol_id FK
        VARCHAR signal_type
        NUMERIC confidence
        NUMERIC predicted_price
        VARCHAR model_version
        BOOLEAN is_premium
    }

    STRATEGIES {
        UUID id PK
        UUID user_id FK
        VARCHAR name
        NUMERIC stop_loss_pct
        NUMERIC take_profit_pct
        JSONB config
    }

    PORTFOLIOS {
        UUID id PK
        UUID user_id FK
        NUMERIC initial_capital
    }

    POSITIONS {
        UUID id PK
        UUID portfolio_id FK
        UUID symbol_id FK
        NUMERIC quantity
        NUMERIC entry_price
        VARCHAR status
    }

    BACKTESTS {
        UUID id PK
        UUID user_id FK
        UUID strategy_id FK
        DATE start_date
        DATE end_date
        VARCHAR status
        JSONB results
    }

    MODEL_VERSIONS {
        UUID id PK
        VARCHAR model_name
        VARCHAR architecture
        JSONB hyperparameters
        JSONB metrics
        BOOLEAN is_active
    }

    TRAINING_RUNS {
        UUID id PK
        UUID model_version_id FK
        VARCHAR status
        JSONB metrics_history
    }

    PREDICTIONS {
        UUID id PK
        UUID model_version_id FK
        VARCHAR symbol_ticker
        VARCHAR predicted_direction
        NUMERIC confidence
    }

    SYMBOLS ||--o{ STOCK_PRICES : has
    SYMBOLS ||--o{ TECHNICAL_INDICATORS : has
    USERS ||--o{ SUBSCRIPTIONS : has
    USERS ||--o{ STRATEGIES : creates
    USERS ||--|| PORTFOLIOS : owns
    USERS ||--o{ BACKTESTS : runs
    PORTFOLIOS ||--o{ POSITIONS : contains
    STRATEGIES ||--o{ BACKTESTS : tested_by
    MODEL_VERSIONS ||--o{ TRAINING_RUNS : trained_in
    MODEL_VERSIONS ||--o{ PREDICTIONS : produces
```

---

## Subscription Tiers

| Feature | FREE | BASIC | PREMIUM |
|---|---|---|---|
| Signals/day | 5 | 50 | Unlimited |
| Symbols tracked | 5 | 25 | Unlimited |
| Backtests/day | 1 | 10 | Unlimited |
| Historical data | 1 year | 5 years | Full |
| Real-time updates | No | 5min delay | Real-time |

---

## Messaging (RabbitMQ)

```mermaid
graph LR
    subgraph ex["Exchanges"]
        E1["trading.market-data<br/><i>topic</i>"]
        E2["trading.predictions<br/><i>topic</i>"]
        E3["trading.signals<br/><i>topic</i>"]
        DLX["trading.dlx<br/><i>fanout</i>"]
    end

    subgraph qu["Queues"]
        Q1["ai-engine<br/>.market-data.prices"]
        Q2["trading-core<br/>.market-data.prices"]
        Q3["ai-engine<br/>.prediction.requests"]
        Q4["trading-core<br/>.prediction.results"]
        Q5["web-app<br/>.signals.new"]
        DLQ["trading<br/>.dead-letters"]
    end

    E1 -->|"prices.updated"| Q1
    E1 -->|"prices.updated"| Q2
    E2 -->|"request.batch"| Q3
    E2 -->|"result.completed"| Q4
    E3 -->|"signal.generated"| Q5
    DLX --> DLQ

    style E1 fill:#f97316,stroke:#ea580c,color:#ffffff,stroke-width:2px
    style E2 fill:#f97316,stroke:#ea580c,color:#ffffff,stroke-width:2px
    style E3 fill:#f97316,stroke:#ea580c,color:#ffffff,stroke-width:2px
    style DLX fill:#ef4444,stroke:#dc2626,color:#ffffff,stroke-width:2px
    style Q1 fill:#8b5cf6,stroke:#7c3aed,color:#ffffff
    style Q2 fill:#10b981,stroke:#059669,color:#ffffff
    style Q3 fill:#8b5cf6,stroke:#7c3aed,color:#ffffff
    style Q4 fill:#10b981,stroke:#059669,color:#ffffff
    style Q5 fill:#3b82f6,stroke:#2563eb,color:#ffffff
    style DLQ fill:#6b7280,stroke:#4b5563,color:#ffffff
```

> **Legend:**
> $\color{#f97316}{\textsf{Orange}}$ = Exchanges |
> $\color{#8b5cf6}{\textsf{Purple}}$ = AI engine queues |
> $\color{#10b981}{\textsf{Green}}$ = Trading core queues |
> $\color{#3b82f6}{\textsf{Blue}}$ = Web app queues |
> $\color{#ef4444}{\textsf{Red}}$ = Dead letter

---

## Data Pipeline (End-to-End)

```mermaid
flowchart TD
    YF["Yahoo Finance API"]
    MD["market-data-service"]
    PG1[("PostgreSQL")]
    RMQ1["RabbitMQ"]
    AI["ai-engine"]
    FE["Feature Engineering<br/><i>60-day window · 17 features</i>"]
    NORM["Normalize<br/><i>MinMaxScaler</i>"]
    CNN["CNN Forward Pass"]
    SOFT["Softmax<br/><i>P(DOWN) · P(NEUTRAL) · P(UP)</i>"]
    RMQ2["RabbitMQ"]
    TC["trading-core-service"]
    STRAT["Apply Strategy Rules<br/><i>Risk management</i>"]
    SIG["Generate TradingSignal<br/><i>BUY / SELL / HOLD</i>"]
    PG2[("PostgreSQL")]
    WS["WebSocket<br/><i>to subscribed users</i>"]

    YF -->|"Scheduled fetch<br/>weekdays 6pm EST"| MD
    MD -->|"Store OHLCV"| PG1
    MD -->|"Publish event"| RMQ1
    RMQ1 -->|"market-data.prices.updated"| AI

    AI --> FE --> NORM --> CNN --> SOFT

    SOFT -->|"Publish result"| RMQ2
    RMQ2 -->|"prediction.result.completed"| TC
    TC --> STRAT --> SIG
    SIG -->|"Store"| PG2
    SIG -->|"Notify"| WS

    style YF fill:#6b7280,stroke:#4b5563,color:#ffffff,stroke-width:2px
    style MD fill:#10b981,stroke:#059669,color:#ffffff,stroke-width:2px
    style PG1 fill:#f59e0b,stroke:#d97706,color:#1f2937,stroke-width:2px
    style RMQ1 fill:#f97316,stroke:#ea580c,color:#ffffff,stroke-width:2px
    style AI fill:#8b5cf6,stroke:#7c3aed,color:#ffffff,stroke-width:2px
    style FE fill:#a78bfa,stroke:#8b5cf6,color:#ffffff
    style NORM fill:#a78bfa,stroke:#8b5cf6,color:#ffffff
    style CNN fill:#7c3aed,stroke:#6d28d9,color:#ffffff,stroke-width:3px
    style SOFT fill:#a78bfa,stroke:#8b5cf6,color:#ffffff
    style RMQ2 fill:#f97316,stroke:#ea580c,color:#ffffff,stroke-width:2px
    style TC fill:#10b981,stroke:#059669,color:#ffffff,stroke-width:2px
    style STRAT fill:#34d399,stroke:#10b981,color:#064e3b
    style SIG fill:#fbbf24,stroke:#f59e0b,color:#1f2937,stroke-width:3px
    style PG2 fill:#f59e0b,stroke:#d97706,color:#1f2937,stroke-width:2px
    style WS fill:#3b82f6,stroke:#2563eb,color:#ffffff,stroke-width:2px
```

---

## Verification Plan

After each phase, verify:

1. **Phase 1**: `docker-compose up` -> PostgreSQL/Redis/RabbitMQ start -> `market-data-service` starts -> `curl GET /actuator/health` returns UP -> trigger ingestion -> verify OHLCV data in DB -> `GET /api/v1/prices/AAPL/history` returns data
2. **Phase 2**: `ai-engine` starts -> `GET /health` -> train model via `POST /api/v1/models/train` -> `POST /api/v1/predict` returns prediction with direction + confidence
3. **Phase 3**: Register user -> login (get JWT) -> create strategy -> `GET /signals` returns signals -> verify rate limiting for FREE tier
4. **Phase 4**: `web-app` at localhost:3000 -> landing page renders -> login flow works -> dashboard shows signals + charts
5. **Phase 5**: Create backtest via UI -> poll until completed -> view equity curve, metrics, trade list -> compare vs benchmark
6. **Phase 6**: `docker-compose -f docker-compose.prod.yml up` -> all services healthy -> k8s manifests apply cleanly -> CI pipeline passes

Run tests per service:
- Java: `mvn test` (unit) + `mvn verify` (integration with Testcontainers)
- Python: `pytest tests/unit` + `pytest tests/integration`
- Frontend: `npm test` + `npx playwright test`
