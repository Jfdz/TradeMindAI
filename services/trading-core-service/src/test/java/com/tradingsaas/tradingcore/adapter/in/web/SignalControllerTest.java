package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.SignalOutcome;
import com.tradingsaas.tradingcore.domain.model.SignalPerformance;
import com.tradingsaas.tradingcore.domain.model.SignalPerformanceStat;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.in.GetSignalPerformanceUseCase;
import com.tradingsaas.tradingcore.domain.port.in.GetSignalsUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class SignalControllerTest {

    @Test
    void mapsDomainSignalToApiResponse() {
        TradingSignal signal = new TradingSignal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "NVDA",
                SignalType.SELL,
                new Confidence(new BigDecimal("0.61")),
                Timeframe.HOUR_1,
                Instant.parse("2026-04-17T10:00:00Z"),
                new BigDecimal("2.00"),
                new BigDecimal("4.00"),
                new BigDecimal("-1.50"),
                new BigDecimal("430.50"));

        SignalController controller = new SignalController(new StubUseCase(signal), new StubPerformanceUseCase());

        Page<SignalController.SignalResponse> page = controller.listSignals(PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("NVDA", page.getContent().get(0).symbol());
        assertEquals("SELL", page.getContent().get(0).type());
        assertEquals(new BigDecimal("-1.50"), page.getContent().get(0).predictedChangePct());
        assertEquals(new BigDecimal("430.50"), page.getContent().get(0).entryPrice());
        // No performance row for this signal -> outcome fields are null.
        assertEquals(null, page.getContent().get(0).outcome());
    }

    @Test
    void mergesPerformanceOutcomeWhenPresent() {
        UUID signalId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        TradingSignal signal = new TradingSignal(
                signalId,
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "NVDA", SignalType.BUY, new Confidence(new BigDecimal("0.91")),
                Timeframe.DAILY, Instant.parse("2026-04-17T10:00:00Z"),
                new BigDecimal("2.00"), new BigDecimal("4.00"), new BigDecimal("1.50"),
                new BigDecimal("430.50"));
        SignalPerformance perf = new SignalPerformance(
                signalId, "NVDA", signal.getGeneratedAt(), new BigDecimal("430.50"),
                null, null, null, new BigDecimal("450.00"),
                new BigDecimal("0.0480"), new BigDecimal("-0.0210"),
                SignalOutcome.WIN, Instant.parse("2026-04-20T10:00:00Z"), Instant.now());

        SignalController controller = new SignalController(
                new StubUseCase(signal), new StubPerformanceUseCase(Map.of(signalId, perf)));

        SignalController.SignalResponse response = controller.getById(signalId);
        assertEquals("WIN", response.outcome());
        assertEquals(new BigDecimal("0.0480"), response.maxProfit());
        assertEquals(new BigDecimal("450.00"), response.price30d());
    }

    @Test
    void throwsNotFoundWhenLatestMissing() {
        SignalController controller = new SignalController(new StubUseCase(null), new StubPerformanceUseCase());
        assertThrows(SignalController.SignalNotFoundException.class, controller::getLatest);
    }

    private static final class StubUseCase implements GetSignalsUseCase {
        private final TradingSignal signal;

        private StubUseCase(TradingSignal signal) {
            this.signal = signal;
        }

        @Override
        public Page<TradingSignal> getSignals(org.springframework.data.domain.Pageable pageable) {
            return signal == null ? Page.empty(pageable) : new PageImpl<>(java.util.List.of(signal), pageable, 1);
        }

        @Override
        public Optional<TradingSignal> getLatest() {
            return Optional.ofNullable(signal);
        }

        @Override
        public Optional<TradingSignal> getById(UUID id) {
            return Optional.ofNullable(signal).filter(s -> s.getId().equals(id));
        }
    }

    private static final class StubPerformanceUseCase implements GetSignalPerformanceUseCase {
        private final Map<UUID, SignalPerformance> performance;

        private StubPerformanceUseCase() {
            this(Map.of());
        }

        private StubPerformanceUseCase(Map<UUID, SignalPerformance> performance) {
            this.performance = performance;
        }

        @Override
        public Optional<SignalPerformance> findOne(UUID signalId) {
            return Optional.ofNullable(performance.get(signalId));
        }

        @Override
        public Map<UUID, SignalPerformance> findFor(Collection<UUID> signalIds) {
            return performance;
        }

        @Override
        public List<SignalPerformanceStat> stats() {
            return List.of();
        }
    }
}
