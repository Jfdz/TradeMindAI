---
name: backend-design
description: Design and scaffold backend features for Trading SaaS Java/Spring Boot services following Clean Architecture and DDD
---

# Backend Design Skill

You are a senior backend architect designing features for the **Trading SaaS** platform. You produce complete, production-ready Java 21 / Spring Boot 3 code following Clean Architecture and Domain-Driven Design.

## Architecture Reference

```
com.tradingsaas.{service}/
├── domain/
│   ├── {context}/
│   │   ├── model/          → Entities, Value Objects, Enums (pure Java)
│   │   ├── port/
│   │   │   ├── in/         → Input ports (use case interfaces)
│   │   │   └── out/        → Output ports (repository, client interfaces)
│   │   └── service/        → Domain services (pure business logic)
├── application/
│   └── usecase/            → Use case implementations (orchestration)
├── adapter/
│   ├── in/
│   │   ├── web/            → REST controllers + DTOs
│   │   ├── messaging/      → RabbitMQ listeners
│   │   └── scheduler/      → @Scheduled jobs
│   └── out/
│       ├── persistence/    → JPA entities, repositories, mappers
│       ├── external/       → REST clients to other services
│       └── messaging/      → RabbitMQ publishers
└── config/                 → Spring configuration beans
```

## Design Process

When asked to design a backend feature, follow this process:

### 1. Identify the Bounded Context
Determine which DDD context this feature belongs to:
- `user` - Authentication, authorization, profiles, subscriptions
- `signal` - Trading signals, confidence scores, AI predictions
- `strategy` - Trading strategies, risk parameters
- `portfolio` - Portfolios, positions, P&L tracking
- `backtest` - Backtesting execution, results, metrics

If the feature spans multiple contexts, design the interaction through events or application-layer orchestration - NEVER cross-context domain imports.

### 2. Design the Domain Model

**Entities** (have identity, mutable state):
```java
public class TradingSignal {
    private final SignalId id;
    private final SymbolId symbolId;
    private final SignalType type;        // BUY, SELL, HOLD
    private final Confidence confidence;  // Value Object [0.0, 1.0]
    private final Money currentPrice;
    private final Money predictedPrice;
    private final Instant generatedAt;

    // Factory method with validation
    public static TradingSignal generate(SymbolId symbolId, PredictionResult prediction,
                                          Money currentPrice) {
        // Domain logic: convert prediction to signal type
        SignalType type = determineSignalType(prediction);
        return new TradingSignal(SignalId.generate(), symbolId, type,
                                  prediction.confidence(), currentPrice,
                                  prediction.predictedPrice(), Instant.now());
    }

    private static SignalType determineSignalType(PredictionResult prediction) {
        if (prediction.direction() == Direction.UP && prediction.confidence().isAbove(0.6)) {
            return SignalType.BUY;
        } else if (prediction.direction() == Direction.DOWN && prediction.confidence().isAbove(0.6)) {
            return SignalType.SELL;
        }
        return SignalType.HOLD;
    }
}
```

**Value Objects** (no identity, immutable):
```java
public record Confidence(double value) {
    public Confidence {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
        }
    }
    public boolean isAbove(double threshold) { return value > threshold; }
    public boolean isHigh() { return value > 0.8; }
}
```

**Domain rules:**
- No Spring annotations. No JPA annotations. Pure Java.
- Use Java records for Value Objects.
- Validate invariants in constructors/factory methods.
- Rich domain model: behavior lives on the entity, not in services.
- Domain events for cross-context communication.

### 3. Design the Ports

**Input ports** (what the outside world can ask):
```java
public interface GenerateSignalUseCase {
    TradingSignal generateSignal(GenerateSignalCommand command);
}

public record GenerateSignalCommand(SymbolId symbolId, StrategyId strategyId) { }
```

**Output ports** (what the domain needs from the outside):
```java
public interface SignalRepository {
    void save(TradingSignal signal);
    Optional<TradingSignal> findById(SignalId id);
    Page<TradingSignal> findByFilters(SignalFilters filters, Pageable pageable);
}

public interface AIPredictionClient {
    PredictionResult predict(SymbolId symbolId, List<Feature> features);
}

public interface SignalEventPublisher {
    void publishSignalGenerated(TradingSignal signal);
}
```

### 4. Implement the Use Case

```java
@RequiredArgsConstructor
public class GenerateSignalUseCaseImpl implements GenerateSignalUseCase {

    private final AIPredictionClient aiClient;
    private final MarketDataClient marketDataClient;
    private final SignalRepository signalRepository;
    private final SignalEventPublisher eventPublisher;

    @Override
    public TradingSignal generateSignal(GenerateSignalCommand command) {
        // 1. Get current price
        Money currentPrice = marketDataClient.getLatestPrice(command.symbolId());

        // 2. Get AI prediction
        PredictionResult prediction = aiClient.predict(command.symbolId(), /* features */);

        // 3. Generate signal (domain logic)
        TradingSignal signal = TradingSignal.generate(
            command.symbolId(), prediction, currentPrice);

        // 4. Persist
        signalRepository.save(signal);

        // 5. Publish event
        eventPublisher.publishSignalGenerated(signal);

        return signal;
    }
}
```

**Rules:**
- Use case orchestrates. Domain model computes.
- No Spring annotations on use case classes (injected via config).
- Handle errors with domain-specific exceptions.
- Transaction boundary is at the use case level.

### 5. Design the Adapters

**REST Controller:**
```java
@RestController
@RequestMapping("/api/v1/signals")
@RequiredArgsConstructor
public class SignalController {

    private final GetSignalsUseCase getSignalsUseCase;
    private final SignalDtoMapper mapper;

    @GetMapping
    @RequiresSubscription(minimumPlan = SubscriptionPlan.FREE)
    public Page<SignalResponse> getSignals(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) SignalType type,
            Pageable pageable) {
        SignalFilters filters = new SignalFilters(symbol, type);
        return getSignalsUseCase.getSignals(filters, pageable)
                .map(mapper::toResponse);
    }
}
```

**JPA Repository Adapter:**
```java
@Repository
@RequiredArgsConstructor
class SignalJpaRepositoryAdapter implements SignalRepository {

    private final SpringDataSignalRepository jpaRepository;
    private final SignalJpaMapper mapper;

    @Override
    public void save(TradingSignal signal) {
        jpaRepository.save(mapper.toJpaEntity(signal));
    }

    @Override
    public Optional<TradingSignal> findById(SignalId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }
}
```

### 6. Database Migration

```sql
-- V{next}__create_{table_name}.sql
CREATE TABLE trading_core.trading_signals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol_id       UUID NOT NULL,
    signal_type     VARCHAR(10) NOT NULL,
    confidence      NUMERIC(4,3) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    current_price   NUMERIC(12,4) NOT NULL,
    predicted_price NUMERIC(12,4),
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_premium      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_signals_symbol_date ON trading_core.trading_signals(symbol_id, generated_at DESC);
```

### 7. Configuration Wiring

```java
@Configuration
class SignalConfig {

    @Bean
    GenerateSignalUseCase generateSignalUseCase(
            AIPredictionClient aiClient,
            MarketDataClient marketDataClient,
            SignalRepository signalRepository,
            SignalEventPublisher eventPublisher) {
        return new GenerateSignalUseCaseImpl(aiClient, marketDataClient,
                                              signalRepository, eventPublisher);
    }
}
```

## Checklist Before Output

- [ ] Domain model has no framework dependencies
- [ ] All external dependencies are behind port interfaces
- [ ] Use case handles happy path + error paths
- [ ] DTOs have Bean Validation annotations
- [ ] Controller is thin (delegates to use case)
- [ ] JPA entity is separate from domain entity with mapper
- [ ] Migration is append-only, schema-explicit, with indexes
- [ ] External calls have Resilience4j circuit breaker

## Output

When designing a feature, output:
1. Bounded context and aggregate identification
2. Domain model (entities, value objects, enums)
3. Port interfaces (input and output)
4. Use case implementation
5. Controller + DTOs
6. JPA entity + repository adapter + mapper
7. Flyway migration SQL
8. Spring configuration
9. Test stubs (what to test, which layer)

## Common Pitfalls

### Lambda Variable Capture Error

**Error:** `local variables referenced from a lambda expression must be final or effectively final`

**Cause:** Java lambdas can only capture variables that are final or effectively final (never modified after assignment).

**Bad:**
```java
BigDecimal marketValue = ZERO;
for (Position p : positions) {
    marketValue = marketValue.add(p.getValue());  // mutating!
}
holdings.stream()
    .map(h -> {
        // ERROR: marketValue is not effectively final
        return percentage(h.getValue(), marketValue);
    })
```

**Good:**
```java
BigDecimal totalMarketValue = ZERO;
for (Position p : positions) {
    totalMarketValue = totalMarketValue.add(p.getValue());
}
// Make it effectively final for the lambda
final BigDecimal finalMarketValue = totalMarketValue;
List<Holding> normalized = holdings.stream()
    .map(h -> {
        return percentage(h.getValue(), finalMarketValue);
    })
    .toList();
```

**Rule:** When using a variable inside a lambda, declare it as `final` right before the lambda use, or calculate the value inside the lambda without external variable mutation.
