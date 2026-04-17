package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tradingsaas.tradingcore.domain.model.Confidence;
import com.tradingsaas.tradingcore.domain.model.SignalType;
import com.tradingsaas.tradingcore.domain.model.Timeframe;
import com.tradingsaas.tradingcore.domain.model.TradingSignal;
import com.tradingsaas.tradingcore.domain.port.in.GetSignalsUseCase;
import java.math.BigDecimal;
import java.time.Instant;
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
                SignalType.SELL,
                new Confidence(new BigDecimal("0.61")),
                Timeframe.HOUR_1,
                Instant.parse("2026-04-17T10:00:00Z"),
                new BigDecimal("2.00"),
                new BigDecimal("4.00"));

        SignalController controller = new SignalController(new StubUseCase(signal));

        Page<SignalController.SignalResponse> page = controller.listSignals(PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("SELL", page.getContent().get(0).type());
    }

    @Test
    void throwsNotFoundWhenLatestMissing() {
        SignalController controller = new SignalController(new StubUseCase(null));
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
}
