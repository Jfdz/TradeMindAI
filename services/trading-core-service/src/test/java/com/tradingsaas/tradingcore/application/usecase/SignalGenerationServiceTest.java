package com.tradingsaas.tradingcore.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.ReasoningStatus;
import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.model.backtest.OhlcvBar;
import com.tradingsaas.tradingcore.domain.port.out.HistoricalMarketDataPort;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.time.Instant;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
        private TradingSignal savedSignal;

        @Override
        public TradingSignal save(TradingSignal signal) {
            this.savedSignal = signal;
            return signal;
        }

        @Override
        public Page<TradingSignal> findAll(Pageable pageable) {
            return Page.empty(pageable);
        }

        @Override
        public java.util.Optional<TradingSignal> findById(UUID id) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<TradingSignal> findLatest() {
            return java.util.Optional.empty();
        }

        @Override
        public void updateReasoning(java.util.UUID id, String reasoning, ReasoningStatus status, Instant at) {}
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
