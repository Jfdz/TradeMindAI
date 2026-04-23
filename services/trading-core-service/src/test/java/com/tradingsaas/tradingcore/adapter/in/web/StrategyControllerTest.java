package com.tradingsaas.tradingcore.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tradingsaas.tradingcore.adapter.in.web.dto.StrategyRequest;
import com.tradingsaas.tradingcore.adapter.in.web.dto.StrategyResponse;
import com.tradingsaas.tradingcore.domain.model.RiskParameters;
import com.tradingsaas.tradingcore.domain.model.Strategy;
import com.tradingsaas.tradingcore.domain.model.TokenClaims;
import com.tradingsaas.tradingcore.domain.exception.StrategyNotFoundException;
import com.tradingsaas.tradingcore.domain.port.in.ManageStrategiesUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class StrategyControllerTest {

    @Test
    void mapsCreateRequestToResponse() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Strategy created = strategy();
        StrategyController controller = new StrategyController(new StubUseCase(created));

        StrategyResponse response = controller.createStrategy(
                new TokenClaims(userId, "user@example.com", "PREMIUM"),
                new StrategyRequest("  Trend Following  ", "desc", new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("10.00"), true)
        );

        assertEquals("Trend Following", response.name());
        assertEquals(userId, response.userId());
        assertEquals(0, response.riskParameters().stopLossPct().compareTo(new BigDecimal("2.00")));
    }

    @Test
    void throwsNotFoundWhenDeleteFails() {
        StrategyController controller = new StrategyController(new StubUseCase(null));
        assertThrows(StrategyNotFoundException.class, () ->
                controller.deleteStrategy(new TokenClaims(UUID.randomUUID(), "user@example.com", "FREE"), UUID.randomUUID()));
    }

    private static Strategy strategy() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        return new Strategy(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                userId,
                "Trend Following",
                "desc",
                new RiskParameters(new BigDecimal("2.00"), new BigDecimal("5.00"), new BigDecimal("10.00")),
                true,
                Instant.parse("2026-04-17T10:00:00Z"),
                Instant.parse("2026-04-17T10:00:00Z"));
    }

    private static final class StubUseCase implements ManageStrategiesUseCase {
        private final Strategy strategy;

        private StubUseCase(Strategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public Page<Strategy> getStrategies(UUID userId, org.springframework.data.domain.Pageable pageable) {
            return strategy == null ? Page.empty(pageable) : new PageImpl<>(java.util.List.of(strategy), pageable, 1);
        }

        @Override
        public Strategy createStrategy(UUID userId, StrategyCommand command) {
            return strategy;
        }

        @Override
        public Strategy updateStrategy(UUID userId, UUID strategyId, StrategyCommand command) {
            return strategy;
        }

        @Override
        public void deleteStrategy(UUID userId, UUID strategyId) {
            if (strategy == null) {
                throw new StrategyNotFoundException("Strategy not found: " + strategyId);
            }
        }
    }
}
