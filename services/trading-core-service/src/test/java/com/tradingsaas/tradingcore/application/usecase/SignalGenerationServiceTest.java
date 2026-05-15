package com.tradingsaas.tradingcore.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class SignalGenerationServiceTest {

    @Test
    void generatesBuySignalWithDefaultRiskParameters() {
        UUID symbolId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        AiPrediction prediction = new AiPrediction(
                "AAPL",
                SignalType.BUY,
                new Confidence(new BigDecimal("0.85")),
                new BigDecimal("1.50"),
                List.of(new BigDecimal("0.1"), new BigDecimal("0.8"), new BigDecimal("0.1")),
                Instant.parse("2026-04-17T10:00:00Z"));

        RecordingRepository repository = new RecordingRepository();
        StubMarketDataPort marketDataPort = new StubMarketDataPort(Map.of("AAPL", new BigDecimal("182.50")));
        SignalGenerationService service = new SignalGenerationService(repository, marketDataPort);

        TradingSignal generated = service.generate(symbolId, prediction);

        assertNotNull(repository.savedSignal);
        assertEquals(generated, repository.savedSignal);
        assertEquals(symbolId, generated.getSymbolId());
        assertEquals("AAPL", generated.getTicker());
        assertEquals(SignalType.BUY, generated.getType());
        assertEquals(new BigDecimal("2.00"), generated.getStopLossPct());
        assertEquals(new BigDecimal("4.00"), generated.getTakeProfitPct());
        assertEquals(new BigDecimal("1.50"), generated.getPredictedChangePct());
        assertEquals(new BigDecimal("182.50"), generated.getEntryPrice());
        assertEquals(0, generated.getTargetPrice().compareTo(new BigDecimal("189.800000")));
        assertEquals(0, generated.getStopLoss().compareTo(new BigDecimal("178.850000")));
        assertEquals(0, generated.getExpectedMovePct().compareTo(new BigDecimal("4.0000")));
    }

    @Test
    void holdSignalLeavesDerivedPricesNull() {
        UUID symbolId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        AiPrediction prediction = new AiPrediction(
                "MSFT",
                SignalType.HOLD,
                new Confidence(new BigDecimal("0.55")),
                BigDecimal.ZERO,
                List.of(),
                Instant.parse("2026-04-17T10:00:00Z"));

        RecordingRepository repository = new RecordingRepository();
        SignalGenerationService service = new SignalGenerationService(repository,
                new StubMarketDataPort(Map.of("MSFT", new BigDecimal("400.00"))));

        TradingSignal generated = service.generate(symbolId, prediction);

        assertNull(generated.getTargetPrice());
        assertNull(generated.getStopLoss());
        assertNull(generated.getExpectedMovePct());
    }

    @Test
    void nullEntryPriceLeavesDerivedPricesNull() {
        UUID symbolId = UUID.fromString("12121212-1212-1212-1212-121212121212");
        AiPrediction prediction = new AiPrediction(
                "TSLA",
                SignalType.SELL,
                new Confidence(new BigDecimal("0.70")),
                new BigDecimal("-2.00"),
                List.of(),
                Instant.parse("2026-04-17T10:00:00Z"));

        RecordingRepository repository = new RecordingRepository();
        SignalGenerationService service = new SignalGenerationService(repository, new StubMarketDataPort(Map.of()));

        TradingSignal generated = service.generate(symbolId, prediction);

        assertNull(generated.getEntryPrice());
        assertNull(generated.getTargetPrice());
        assertNull(generated.getStopLoss());
        assertNull(generated.getExpectedMovePct());
    }

    @Test
    void generatesHoldSignalWithoutRiskParameters() {
        UUID symbolId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        AiPrediction prediction = new AiPrediction(
                "MSFT",
                SignalType.HOLD,
                new Confidence(new BigDecimal("0.55")),
                BigDecimal.ZERO,
                List.of(),
                Instant.parse("2026-04-17T10:00:00Z"));

        RecordingRepository repository = new RecordingRepository();
        SignalGenerationService service = new SignalGenerationService(repository, new StubMarketDataPort(Map.of()));

        TradingSignal generated = service.generate(symbolId, prediction);

        assertEquals(SignalType.HOLD, generated.getType());
        assertNull(generated.getStopLossPct());
        assertNull(generated.getTakeProfitPct());
    }

    @Test
    void persistsNullEntryPriceWhenMarketDataUnavailable() {
        UUID symbolId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        AiPrediction prediction = new AiPrediction(
                "TSLA",
                SignalType.SELL,
                new Confidence(new BigDecimal("0.70")),
                new BigDecimal("-2.00"),
                List.of(),
                Instant.parse("2026-04-17T10:00:00Z"));

        RecordingRepository repository = new RecordingRepository();
        SignalGenerationService service = new SignalGenerationService(repository, new StubMarketDataPort(Map.of()));

        TradingSignal generated = service.generate(symbolId, prediction);

        assertNull(generated.getEntryPrice());
    }

    @Test
    void skipsInsertAndReturnsExistingWhenEquivalentSignalAlreadyPresent() {
        UUID symbolId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        AiPrediction prediction = new AiPrediction(
                "NVDA",
                SignalType.BUY,
                new Confidence(new BigDecimal("0.70")),
                new BigDecimal("2.30"),
                List.of(),
                Instant.parse("2026-04-17T10:00:00Z"));

        TradingSignal existing = new TradingSignal(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                symbolId, "NVDA", SignalType.BUY,
                new Confidence(new BigDecimal("0.70")), Timeframe.DAILY,
                Instant.parse("2026-04-17T08:00:00Z"),
                new BigDecimal("2.00"), new BigDecimal("4.00"),
                new BigDecimal("2.30"), new BigDecimal("450.00"),
                new BigDecimal("468.000000"), new BigDecimal("441.000000"),
                new BigDecimal("4.0000"));
        RecordingRepository repository = new RecordingRepository(existing);
        SignalGenerationService service = new SignalGenerationService(
                repository, new StubMarketDataPort(Map.of("NVDA", new BigDecimal("450.00"))));

        TradingSignal generated = service.generate(symbolId, prediction);

        assertEquals(existing.getId(), generated.getId());
        assertEquals(0, repository.saveCallCount);
    }

    @Test
    void selfHealsDerivedPricesWhenExistingSignalHasNulls() {
        UUID symbolId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        AiPrediction prediction = new AiPrediction(
                "NVDA",
                SignalType.BUY,
                new Confidence(new BigDecimal("0.70")),
                new BigDecimal("2.30"),
                List.of(),
                Instant.parse("2026-04-17T10:00:00Z"));

        // Legacy (pre-V21) row with null derived prices.
        TradingSignal legacy = new TradingSignal(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                symbolId, "NVDA", SignalType.BUY,
                new Confidence(new BigDecimal("0.70")), Timeframe.DAILY,
                Instant.parse("2026-04-17T08:00:00Z"),
                new BigDecimal("2.00"), new BigDecimal("4.00"),
                new BigDecimal("2.30"), new BigDecimal("450.00"));
        RecordingRepository repository = new RecordingRepository(legacy);
        SignalGenerationService service = new SignalGenerationService(
                repository, new StubMarketDataPort(Map.of("NVDA", new BigDecimal("450.00"))));

        TradingSignal generated = service.generate(symbolId, prediction);

        assertEquals(legacy.getId(), generated.getId());
        assertEquals(1, repository.saveCallCount);
        assertEquals(0, generated.getTargetPrice().compareTo(new BigDecimal("468.000000")));
        assertEquals(0, generated.getStopLoss().compareTo(new BigDecimal("441.000000")));
        assertEquals(0, generated.getExpectedMovePct().compareTo(new BigDecimal("4.0000")));
    }

    @Test
    void insertsNewSignalWhenSameTickerButDifferentEntryPrice() {
        UUID symbolId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        AiPrediction prediction = new AiPrediction(
                "NVDA",
                SignalType.BUY,
                new Confidence(new BigDecimal("0.70")),
                new BigDecimal("2.30"),
                List.of(),
                Instant.parse("2026-04-17T15:00:00Z"));

        TradingSignal existing = new TradingSignal(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                symbolId, "NVDA", SignalType.BUY,
                new Confidence(new BigDecimal("0.70")), Timeframe.DAILY,
                Instant.parse("2026-04-17T08:00:00Z"),
                new BigDecimal("2.00"), new BigDecimal("4.00"),
                new BigDecimal("2.30"), new BigDecimal("450.00"));
        RecordingRepository repository = new RecordingRepository(existing);
        // Different entry_price -> not a duplicate.
        SignalGenerationService service = new SignalGenerationService(
                repository, new StubMarketDataPort(Map.of("NVDA", new BigDecimal("455.20"))));

        TradingSignal generated = service.generate(symbolId, prediction);

        assertEquals(1, repository.saveCallCount);
        assertEquals(new BigDecimal("455.20"), generated.getEntryPrice());
    }

    @Test
    void persistsNullEntryPriceWhenMarketDataThrows() {
        UUID symbolId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        AiPrediction prediction = new AiPrediction(
                "GOOGL",
                SignalType.BUY,
                new Confidence(new BigDecimal("0.80")),
                new BigDecimal("1.20"),
                List.of(),
                Instant.parse("2026-04-17T10:00:00Z"));

        RecordingRepository repository = new RecordingRepository();
        SignalGenerationService service = new SignalGenerationService(repository, new ThrowingMarketDataPort());

        TradingSignal generated = service.generate(symbolId, prediction);

        assertNull(generated.getEntryPrice());
    }

    private static final class RecordingRepository implements TradingSignalRepository {
        TradingSignal savedSignal;
        int saveCallCount;
        TradingSignal preExisting;

        RecordingRepository() {}

        RecordingRepository(TradingSignal preExisting) {
            this.preExisting = preExisting;
        }

        @Override
        public TradingSignal save(TradingSignal signal) {
            this.savedSignal = signal;
            this.saveCallCount++;
            return signal;
        }

        @Override
        public Page<TradingSignal> findAll(Pageable pageable) {
            return Page.empty(pageable);
        }

        @Override
        public Optional<TradingSignal> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<TradingSignal> findLatest() {
            return Optional.empty();
        }

        @Override
        public void updateReasoning(UUID id, String reasoning, ReasoningStatus status, Instant at) {}

        @Override
        public boolean updateReasoningArtifact(
                UUID id, String reasoning, ReasoningStatus status, Instant at,
                com.tradingsaas.tradingcore.domain.model.ReasoningArtifact artifact) {
            return false;
        }

        @Override
        public org.springframework.data.domain.Page<TradingSignal> findAdminSignals(
                String tickerFilter, org.springframework.data.domain.Pageable pageable) {
            return org.springframework.data.domain.Page.empty();
        }

        @Override
        public java.util.List<String> findDistinctTickers() {
            return java.util.List.of();
        }

        @Override
        public Optional<TradingSignal> findRecentEquivalent(
                String ticker, SignalType signalType, Timeframe timeframe,
                BigDecimal entryPrice, Instant sinceAtLeast) {
            if (preExisting == null) return Optional.empty();
            boolean matches = ticker.equals(preExisting.getTicker())
                    && signalType == preExisting.getType()
                    && timeframe == Timeframe.DAILY
                    && java.util.Objects.equals(entryPrice, preExisting.getEntryPrice());
            return matches ? Optional.of(preExisting) : Optional.empty();
        }
    }

    private static final class StubMarketDataPort implements HistoricalMarketDataPort {
        private final Map<String, BigDecimal> prices;

        StubMarketDataPort(Map<String, BigDecimal> prices) {
            this.prices = prices;
        }

        @Override
        public List<OhlcvBar> loadHistoricalBars(String symbol, LocalDate from, LocalDate to) {
            return List.of();
        }

        @Override
        public Map<String, BigDecimal> loadLatestPrices(List<String> symbols) {
            return prices;
        }

        @Override
        public boolean hasData(String symbol) {
            return prices.containsKey(symbol);
        }
    }

    private static final class ThrowingMarketDataPort implements HistoricalMarketDataPort {
        @Override
        public List<OhlcvBar> loadHistoricalBars(String symbol, LocalDate from, LocalDate to) {
            return List.of();
        }

        @Override
        public Map<String, BigDecimal> loadLatestPrices(List<String> symbols) {
            throw new RuntimeException("market-data unreachable");
        }

        @Override
        public boolean hasData(String symbol) {
            return false;
        }
    }
}
