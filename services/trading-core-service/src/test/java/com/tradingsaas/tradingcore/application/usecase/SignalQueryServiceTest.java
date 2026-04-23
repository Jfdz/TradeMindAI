package com.tradingsaas.tradingcore.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.out.TradingSignalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class SignalQueryServiceTest {

    @Test
    void delegatesReadOperationsToRepository() {
        TradingSignal signal = new TradingSignal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                SignalType.BUY,
                new Confidence(new BigDecimal("0.85")),
                Timeframe.DAILY,
                Instant.parse("2026-04-17T10:00:00Z"),
                new BigDecimal("2.00"),
                new BigDecimal("4.00"));

        RecordingRepository repository = new RecordingRepository(signal);
        SignalQueryService service = new SignalQueryService(repository);

        Page<TradingSignal> page = service.getSignals(PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertTrue(service.getLatest().isPresent());
        assertEquals(signal, service.getById(signal.getId()).orElseThrow());
    }

    private static final class RecordingRepository implements TradingSignalRepository {
        private final TradingSignal signal;

        private RecordingRepository(TradingSignal signal) {
            this.signal = signal;
        }

        @Override
        public TradingSignal save(TradingSignal signal) {
            return signal;
        }

        @Override
        public Page<TradingSignal> findAll(org.springframework.data.domain.Pageable pageable) {
            return new PageImpl<>(List.of(signal), pageable, 1);
        }

        @Override
        public Optional<TradingSignal> findById(UUID id) {
            return signal.getId().equals(id) ? Optional.of(signal) : Optional.empty();
        }

        @Override
        public Optional<TradingSignal> findLatest() {
            return Optional.of(signal);
        }
    }
}
