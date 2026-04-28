# Operations & Growth Plan — TradeMind SaaS

## Context

TradeMind is a 4-service microservices platform (market-data-service, ai-engine, trading-core-service, web-app) that is architecturally complete but has three broken links preventing any signal generation, plus a portfolio feature users can't interact with. This plan fixes the pipeline end-to-end (Sprint A), enables portfolio position management (Sprint B), adds operational monitoring (Sprint C), and enriches the dashboard (Sprint D).

---

## Confirmed Bugs (from codebase read)

| # | File | Line | What's broken |
|---|---|---|---|
| A-1 | `services/ai-engine/src/ai_engine/main.py` | 43, 77-78 | `_start_consumers()` is sync; creates consumer objects but never calls `await consumer.start()` |
| A-1b | `services/ai-engine/src/ai_engine/main.py` | 73-75 | `_predict` closure returns `[]` unconditionally |
| A-2 | `services/ai-engine/src/ai_engine/adapters/in_/prediction.py` | 70-73 | `_fetch_ohlcv()` raises `NotImplementedError` |
| A-3 | `services/ai-engine/src/ai_engine/main.py` | 78 | `MarketDataEventConsumer` gets `lambda syms: None` — no prediction triggered |
| A-5 | `services/ai-engine/src/ai_engine/adapters/in_/training.py` | 51-53 | `_run_training` does `await asyncio.sleep(0)` then marks COMPLETED — no real training |
| A-5b | `services/ai-engine/src/ai_engine/main.py` | 39 | `app.state.model_loaded` is set to `False` at startup but never set to `True` — `/ready` always 503 |
| B-1 | `services/trading-core-service/src/main/java/.../PortfolioController.java` | — | Only `GET /api/v1/portfolio`; no POST/PUT/DELETE/close endpoints |
| B-2 | `services/trading-core-service/src/main/java/.../PortfolioOverviewService.java` | 93 | `realizedPnl` hardcoded to `BigDecimal.ZERO` |
| B-DLQ | `services/trading-core-service/src/main/java/.../PredictionResultListener.java` | — | No DLQ — malformed messages on `trading-core.prediction.result.completed` are lost |

---

## Pipeline Flow (Target State)

```
[market-data-service @ 6 PM ET]
    └─ publishes "market-data.prices.updated" to RabbitMQ
              │
              ▼  queue: ai-engine.market-data.prices
[ai-engine — MarketDataEventConsumer]
    └─ trigger_fn(symbols) → async task:
         ├─ fetch OHLCV via GET http://market-data-service:8081/api/v1/prices/{ticker}/history
         ├─ compute features → CNN forward pass
         └─ publish predictions to "prediction.result.completed" FANOUT exchange
              │
              ▼  queue: trading-core.prediction.result.completed
[trading-core-service — PredictionResultListener]
    └─ generateSignal → TradingSignalJpaEntity saved
              │
              ▼
[web-app — GET /api/v1/signals]
    └─ fresh signals visible in dashboard
```

---

## Sprint A — Fix the Pipeline

### A-1: Fix consumer startup in `main.py`

**File:** `services/ai-engine/src/ai_engine/main.py`

Convert `_start_consumers` from sync to async and await it inside the `lifespan` context manager. Call `await consumer.start()` for both consumers. Also load the model in lifespan and set `app.state.model_loaded = True`.

```python
@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.model_loaded = False
    app.state.prediction_service = None
    app.state.consumers = []

    await _start_consumers(app)   # was: _start_consumers(app)

    yield
    ...
```

Inside `_start_consumers`, after creating each consumer, call `await consumer.start()`. Add model loading:

```python
async def _start_consumers(app: FastAPI) -> None:
    try:
        from ai_engine.adapters.out.rabbitmq_consumer import MarketDataEventConsumer, PredictionRequestConsumer
        from ai_engine.config import get_settings
        from ai_engine.core.use_cases.model_registry import ModelRegistry
        from ai_engine.core.use_cases.prediction_service import PredictionService

        settings = get_settings()
        registry = ModelRegistry(settings.model_path)
        svc = PredictionService(registry)
        app.state.prediction_service = svc

        # Try loading model; if no active version, model_loaded stays False
        try:
            svc.reload()
            app.state.model_loaded = True
        except RuntimeError:
            logger.warning("No active model version — predictions disabled until model is trained")

        async def _trigger(symbols: list[str]) -> None:
            # wired in A-3
            pass

        pred_consumer = PredictionRequestConsumer(settings.rabbitmq_url, _sync_predict(app))
        mde_consumer = MarketDataEventConsumer(settings.rabbitmq_url, _trigger)
        app.state.consumers = [pred_consumer, mde_consumer]

        await pred_consumer.start()
        await mde_consumer.start()
    except Exception:
        logger.warning("RabbitMQ consumers not started")
```

**Acceptance criteria:** `GET /health` → 200; consumers appear in RabbitMQ Management queues.

### A-2: Implement `_fetch_ohlcv()` and `MarketDataClient`

**New file:** `services/ai-engine/src/ai_engine/adapters/out/market_data_client.py`

```python
import httpx
import pandas as pd

class MarketDataClient:
    def __init__(self, base_url: str):
        self._base_url = base_url

    def fetch_ohlcv(self, ticker: str, size: int = 100) -> pd.DataFrame:
        url = f"{self._base_url}/api/v1/prices/{ticker}/history"
        resp = httpx.get(url, params={"timeframe": "DAILY", "size": size}, timeout=10)
        resp.raise_for_status()
        bars = resp.json().get("content", [])
        if not bars:
            raise ValueError(f"No OHLCV data for {ticker}")
        records = [
            {
                "date": b["date"],
                "open": b["ohlcv"]["open"],
                "high": b["ohlcv"]["high"],
                "low": b["ohlcv"]["low"],
                "close": b["ohlcv"]["close"],
                "volume": b["ohlcv"]["volume"],
            }
            for b in reversed(bars)  # API returns newest-first; model wants oldest-first
        ]
        return pd.DataFrame(records)
```

**Config change** — `services/ai-engine/src/ai_engine/config.py`: add `market_data_service_url: str = "http://market-data-service:8081"`.

**`prediction.py` change** — replace `_fetch_ohlcv`:

```python
def _fetch_ohlcv(ticker: str):
    from ai_engine.adapters.out.market_data_client import MarketDataClient
    from ai_engine.config import get_settings
    return MarketDataClient(get_settings().market_data_service_url).fetch_ohlcv(ticker)
```

**Acceptance criteria:** `POST /api/v1/predict` with `{"ticker":"AAPL"}` returns prediction JSON (needs model loaded per A-1/A-5).

### A-3: Wire `MarketDataEventConsumer.trigger_fn` to batch predict + publish

**File:** `services/ai-engine/src/ai_engine/main.py`

Replace the `_trigger` stub from A-1 with a real async implementation. The `MarketDataEventConsumer.on_message` is already `async`, so `trigger_fn` can be an async callable:

```python
async def _trigger(symbols: list[str]) -> None:
    if not app.state.model_loaded:
        logger.warning("Model not loaded; skipping prediction trigger")
        return
    svc: PredictionService = app.state.prediction_service
    client = MarketDataClient(settings.market_data_service_url)

    pairs = []
    for ticker in symbols:
        try:
            df = client.fetch_ohlcv(ticker)
            pairs.append((ticker, df))
        except Exception:
            logger.exception("Failed to fetch OHLCV for %s", ticker)

    if not pairs:
        return

    results = svc.predict_batch(pairs)
    payload = json.dumps({
        "tickers": symbols,
        "predictions": [r.__dict__ for r in results]
    }).encode()
    await result_exchange.publish(Message(body=payload, content_type="application/json"), routing_key="")
```

**`rabbitmq_consumer.py` change** — update `MarketDataEventConsumer.trigger_fn` type hint to `Callable[[list[str]], Awaitable[None]]` and in `on_message` call `await self._trigger_fn(symbols)`.

**Acceptance criteria:** After market-data event fires, new rows appear in `trading_core.trading_signals` within 60s.

### A-4: Add DLQ to trading-core `PredictionResultListener`

**New file:** `services/trading-core-service/src/main/java/com/tradingsaas/tradingcore/config/RabbitMQConfig.java`

```java
@Configuration
public class RabbitMQConfig {

    @Bean
    DirectExchange predictionDlx() {
        return new DirectExchange("dlx.prediction.result.completed", true, false);
    }

    @Bean
    Queue predictionDlq() {
        return QueueBuilder.durable("dlq.prediction.result.completed").build();
    }

    @Bean
    Binding predictionDlqBinding(Queue predictionDlq, DirectExchange predictionDlx) {
        return BindingBuilder.bind(predictionDlq).to(predictionDlx).with("dead");
    }

    @Bean
    Queue predictionResultQueue() {
        return QueueBuilder.durable("trading-core.prediction.result.completed")
                .withArgument("x-dead-letter-exchange", "dlx.prediction.result.completed")
                .withArgument("x-dead-letter-routing-key", "dead")
                .build();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setDefaultRequeueRejected(false); // nack → DLQ, not requeue
        return factory;
    }
}
```

**`PredictionResultListener`** — change `@RabbitListener` to bind to the queue declared above (use `queues = QUEUE_NAME` instead of `@QueueBinding` so RabbitMQConfig owns the queue/exchange declaration).

**Acceptance criteria:** A malformed message on `trading-core.prediction.result.completed` → appears in `dlq.prediction.result.completed`.

### A-5: Model bootstrap + set `model_loaded` flag

**New script:** `services/ai-engine/scripts/seed_model.py`

Saves randomly-initialized `StockCNN` weights via `ModelRegistry.save()` + `ModelRegistry.activate()`. Run once on first deploy.

```python
from ai_engine.core.models.cnn import StockCNN
from ai_engine.core.use_cases.model_registry import ModelRegistry

registry = ModelRegistry("./models")
model = StockCNN()
version_id = registry.save(model, "seed-v0", "StockCNN", {}, {"note": "random-init seed"})
registry.activate(version_id)
print(f"Seed model activated: {version_id}")
```

**`training.py`** — wire `_run_training` to call actual training once data is available (deferred; stub acceptable for seed-model bootstrap). The critical fix for now is A-1b: load model in lifespan and set `model_loaded = True`.

**Acceptance criteria:** `GET /ready` returns 200 after seed model is present.

---

## Sprint B — Portfolio Position Management

### B-1: Add position CRUD endpoints

**Flyway migration** `V9__add_position_management_columns.sql`:

```sql
ALTER TABLE trading_core.positions
    ADD COLUMN IF NOT EXISTS exit_price NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS fees NUMERIC(10,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS notes TEXT,
    ADD COLUMN IF NOT EXISTS purchase_date DATE;
```

**JPA entity** — `PortfolioPositionJpaEntity.java`: add the four new fields with getters + all-args constructor update. Add mutators for `exitPrice`, `closedAt`, `status` (for close operation).

**New JPA repository** — `PortfolioPositionJpaRepository extends JpaRepository<PortfolioPositionJpaEntity, UUID>` with `findByIdAndPortfolioUserId(UUID id, UUID userId)`.

**New use case interface + impl** — `AddPortfolioPositionUseCase`:

```java
public interface AddPortfolioPositionUseCase {
    PortfolioPositionJpaEntity add(UUID userId, AddPositionCommand cmd);
}
```

`AddPortfolioPositionUseCaseImpl`: 
- Find portfolio by userId (or auto-create with default FREE capital if none exists)
- Create `PortfolioPositionJpaEntity` with status=`OPEN`, openedAt=`Instant.now()`
- Save via `PortfolioPositionJpaRepository`

**Controller additions** in `PortfolioController.java`:

```
POST   /api/v1/portfolio/positions           → add position (ticker, quantity, entryPrice, purchaseDate, fees, notes)
PUT    /api/v1/portfolio/positions/{id}      → update (quantity, entryPrice, purchaseDate, fees, notes)
DELETE /api/v1/portfolio/positions/{id}      → delete (only if OPEN)
POST   /api/v1/portfolio/positions/{id}/close → close (exitPrice, closedAt, fees)
```

All endpoints already covered by `.anyRequest().authenticated()` in `SecurityConfig.java` — no security changes needed.

**Acceptance criteria:** `POST /api/v1/portfolio/positions` creates position; `GET /api/v1/portfolio` shows holding with correct market value and unrealizedPnl.

### B-2: Fix realized P&L

**File:** `services/trading-core-service/src/main/java/.../PortfolioOverviewService.java`

Line 93: replace `BigDecimal.ZERO` with:

```java
BigDecimal realizedPnl = portfolio.getPositions().stream()
    .filter(p -> "CLOSED".equals(p.getStatus()) && p.getExitPrice() != null)
    .map(p -> p.getExitPrice().subtract(p.getEntryPrice())
                .multiply(p.getQuantity())
                .subtract(p.getFees() != null ? p.getFees() : BigDecimal.ZERO))
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

**Acceptance criteria:** Closing a position at a profit yields positive `realizedPnl` in `GET /api/v1/portfolio`.

### B-3: "Add Position" form in web-app

**New page:** `services/web-app/app/dashboard/portfolio/add/page.tsx`

Form fields: Ticker (select from `apiClient.getSymbols()`), Quantity, Entry Price, Purchase Date, Fees (optional), Notes (optional). On submit: `apiClient.addPosition(payload)`, then redirect to `/dashboard/portfolio`.

**`api-client.ts` additions:**

```typescript
export type AddPositionPayload = {
  ticker: string;
  quantity: number;
  entryPrice: number;
  purchaseDate: string;
  fees?: number;
  notes?: string;
};

// inside apiClient:
async addPosition(payload: AddPositionPayload): Promise<PortfolioHoldingResponse> {
  return requestJson("/api/v1/portfolio/positions", {
    method: "POST",
    body: JSON.stringify(payload),
  });
},
```

**Portfolio page** — add "Add Position" button linking to `/dashboard/portfolio/add`.

---

## Sprint C — Operational Reliability

### C-1: Add crypto symbols (BTC-USD, ETH-USD)

**File:** `services/market-data-service/src/main/resources/application.yml`

Add to `market-data.ingestion.tracked-symbols` list:
```yaml
- ticker: BTC-USD
  name: Bitcoin USD
  exchange: CRYPTO
  active: true
- ticker: ETH-USD
  name: Ethereum USD
  exchange: CRYPTO
  active: true
```

Yahoo Finance supports these tickers natively — no adapter changes needed.

### C-2: Prometheus alert rules

**New file:** `infrastructure/monitoring/alerts.yml`

```yaml
groups:
  - name: trademind
    rules:
      - alert: SignalGenerationStale
        expr: time() - max(trading_signal_generated_timestamp) > 90000  # 25h
        for: 5m
        annotations:
          summary: "No new signals in last 25 hours"
      - alert: DLQNonEmpty
        expr: rabbitmq_queue_messages{queue=~"dlq.*"} > 0
        for: 1m
        annotations:
          summary: "Dead letter queue has messages"
      - alert: AiEngineDown
        expr: up{job="ai-engine"} == 0
        for: 2m
```

### C-3: Data freshness endpoint

**New controller:** `services/trading-core-service/src/main/java/.../adapter/in/web/DataFreshnessController.java`

```
GET /api/v1/health/data-freshness
```

Queries `MAX(generated_at)` from `trading_core.trading_signals` via a new repository method. Returns `{ lastSignalAt, signalAgeHours, status }` with status FRESH/STALE/CRITICAL.

### C-4: K8s CronJob for model retraining

**New file:** `infrastructure/k8s/base/cronjobs.yml`

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: model-retraining
  namespace: trading-saas
spec:
  schedule: "0 2 * * 0"  # Sunday 02:00 UTC
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: trigger
            image: curlimages/curl:latest
            command: ["curl", "-X", "POST", "http://ai-engine.trading-saas.svc.cluster.local:8000/api/v1/models/train",
                      "-H", "Content-Type: application/json",
                      "-d", "{\"version_tag\":\"weekly\",\"max_epochs\":50}"]
          restartPolicy: OnFailure
```

### C-5: Manual ingestion trigger endpoint

**File:** `services/market-data-service/src/main/java/.../adapter/in/web/IngestionController.java` (already referenced in `SecurityConfig` under `.requestMatchers("/api/v1/ingestion/**").hasRole("ADMIN")`)

```java
@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionController {
    private final ScheduledMarketDataIngestionJob ingestionJob;

    @PostMapping("/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> trigger() {
        CompletableFuture.runAsync(ingestionJob::run);
        return Map.of("status", "TRIGGERED", "timestamp", Instant.now().toString());
    }
}
```

---

## Sprint D — Dashboard Enrichment

### D-1: Signal history chart

**File:** `services/web-app/app/dashboard/page.tsx`

Add line chart using `apiClient.getSignals()` data showing confidence over last 30 signals.

### D-2: Signal impact on portfolio

**New endpoint:** `GET /api/v1/portfolio/signal-impact` in trading-core-service.
Cross-reference open position tickers with latest BUY/SELL signals. Surface as a warning banner in `web-app/app/dashboard/portfolio/page.tsx`.

### D-3: AI model confidence widget

**Data source:** `GET http://ai-engine:8000/api/v1/models/active` (proxied via trading-core-service or directly from web-app with CORS).

---

## Execution Order

```
A-5 (seed model script + set model_loaded in lifespan)
  └─ A-1 (consumer startup — awaits .start())
       └─ A-2 (market data client + _fetch_ohlcv)
            └─ A-3 (trigger_fn wired to batch predict + publish)
                 └─ End-to-end pipeline works

A-4 (DLQ) ─── parallel with A-2

B-1 (V9 migration + CRUD endpoints)
  └─ B-2 (realized PnL)
  └─ B-3 (web-app add position form)

C-1 through C-5 ─── independent, any time after A-3
D-1 through D-3 ─── after A-3 + B-1 done
```

---

## Verification

**Sprint A:**
```bash
# Seed model (run once)
cd services/ai-engine && python scripts/seed_model.py

# Start ai-engine and check ready
# Use `http://ai-engine:8000` from another container on the Docker network or in-cluster.
# Use `http://localhost:8000` only in local dev compose, where the ai-engine port is published.
# In `docker-compose.prod.yml` the ai-engine port is not published to the VM host, so host-side
# checks must use `docker compose exec`, `docker exec`, or an internal caller container.
curl http://ai-engine:8000/ready   # expect {"status":"ready"}
curl http://localhost:8000/ready   # local dev compose only

# Trigger training (POST, not GET)
curl -X POST http://ai-engine:8000/api/v1/models/train \
  -H 'Content-Type: application/json' \
  -d '{"version_tag":"bootstrap-v1"}'

# Manual predict
curl -X POST http://ai-engine:8000/api/v1/predict \
  -H 'Content-Type: application/json' -d '{"ticker":"AAPL"}'

# Check signals after market data event
psql -c "SELECT ticker, signal_type, confidence, generated_at FROM trading_core.trading_signals ORDER BY generated_at DESC LIMIT 5;"
```

**Sprint B:**
```bash
# Add position
curl -X POST http://localhost:8082/api/v1/portfolio/positions \
  -H 'Authorization: Bearer TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"ticker":"AAPL","quantity":10,"entryPrice":170,"purchaseDate":"2026-04-01"}'

# Verify portfolio overview
curl -H 'Authorization: Bearer TOKEN' http://localhost:8082/api/v1/portfolio

# DLQ check
curl -u guest:guest http://localhost:15672/api/queues/%2F/dlq.prediction.result.completed
```

**Sprint C:**
```bash
curl http://localhost:8082/api/v1/health/data-freshness
kubectl get cronjobs -n trading-saas
```
