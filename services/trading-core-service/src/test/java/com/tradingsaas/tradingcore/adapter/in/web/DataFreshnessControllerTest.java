package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tradingsaas.tradingcore.adapter.out.persistence.TradingSignalJpaRepository;
import com.tradingsaas.tradingcore.adapter.out.persistence.entity.TradingSignalJpaEntity;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataFreshnessControllerTest {

    @Test
    void returnsFreshForRecentSignal() {
        TradingSignalJpaRepository repository = mock(TradingSignalJpaRepository.class);
        DataFreshnessController controller = new DataFreshnessController(repository);
        Instant generatedAt = Instant.now().minus(Duration.ofHours(2));

        when(repository.findTopByOrderByGeneratedAtDesc()).thenReturn(Optional.of(signal(generatedAt)));

        Map<String, Object> result = controller.dataFreshness();

        assertEquals(generatedAt.toString(), result.get("lastSignalAt"));
        assertEquals("FRESH", result.get("status"));
    }

    @Test
    void returnsCriticalWhenNoSignalsExist() {
        TradingSignalJpaRepository repository = mock(TradingSignalJpaRepository.class);
        DataFreshnessController controller = new DataFreshnessController(repository);
        when(repository.findTopByOrderByGeneratedAtDesc()).thenReturn(Optional.empty());

        Map<String, Object> result = controller.dataFreshness();

        assertEquals(null, result.get("lastSignalAt"));
        assertEquals("CRITICAL", result.get("status"));
    }

    @Test
    void returnsStaleForOlderSignal() {
        TradingSignalJpaRepository repository = mock(TradingSignalJpaRepository.class);
        DataFreshnessController controller = new DataFreshnessController(repository);
        Instant generatedAt = Instant.now().minus(Duration.ofHours(30));

        when(repository.findTopByOrderByGeneratedAtDesc()).thenReturn(Optional.of(signal(generatedAt)));

        Map<String, Object> result = controller.dataFreshness();

        assertEquals("STALE", result.get("status"));
    }

    private static TradingSignalJpaEntity signal(Instant generatedAt) {
        return new TradingSignalJpaEntity(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                SignalType.BUY,
                new BigDecimal("0.85"),
                Timeframe.DAILY,
                generatedAt,
                new BigDecimal("2.00"),
                new BigDecimal("4.00"));
    }
}
