package com.tradingsaas.tradingcore.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tradingsaas.tradingcore.domain.model.AiPrediction;
import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.out.AiPredictionPort;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class SignalGenerationServiceTest {

    @Test
    void generatesBuySignalWithDefaultRiskParameters() {
        UUID symbolId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        AiPredictionPort predictionPort = ticker -> new AiPrediction(
                ticker,
                SignalType.BUY,
                new Confidence(new BigDecimal("0.85")),
                new BigDecimal("1.50"),
                java.util.List.of(new BigDecimal("0.1"), new BigDecimal("0.8"), new BigDecimal("0.1")),
                Instant.parse("2026-04-17T10:00:00Z"));

        RecordingRepository repository = new RecordingRepository();
        SignalGenerationService service = new SignalGenerationService(predictionPort, repository);

        TradingSignal generated = service.generate(symbolId, "aapl");

        assertNotNull(repository.savedSignal);
        assertEquals(generated, repository.savedSignal);
        assertEquals(symbolId, generated.getSymbolId());
        assertEquals(SignalType.BUY, generated.getType());
        assertEquals(new BigDecimal("2.00"), generated.getStopLossPct());
        assertEquals(new BigDecimal("4.00"), generated.getTakeProfitPct());
    }

    @Test
    void generatesHoldSignalWithoutRiskParameters() {
        UUID symbolId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        AiPredictionPort predictionPort = ticker -> new AiPrediction(
                ticker,
                SignalType.HOLD,
                new Confidence(new BigDecimal("0.55")),
                BigDecimal.ZERO,
                java.util.List.of(),
                Instant.parse("2026-04-17T10:00:00Z"));

        RecordingRepository repository = new RecordingRepository();
        SignalGenerationService service = new SignalGenerationService(predictionPort, repository);

        TradingSignal generated = service.generate(symbolId, "msft");

        assertEquals(SignalType.HOLD, generated.getType());
        assertNull(generated.getStopLossPct());
        assertNull(generated.getTakeProfitPct());
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
    }
}
