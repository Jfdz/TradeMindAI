package com.tradingsaas.tradingcore.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StrategyDomainModelTest {

    @Test
    void strategyNormalizesNameAndStoresRiskParameters() {
        RiskParameters riskParameters = new RiskParameters(
                new BigDecimal("2.00"),
                new BigDecimal("4.00"),
                new BigDecimal("10.00"));

        Strategy strategy = new Strategy(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "  Trend following  ",
                "Daily momentum strategy",
                riskParameters,
                true,
                Instant.parse("2026-04-17T10:00:00Z"),
                Instant.parse("2026-04-17T10:05:00Z"));

        assertEquals("Trend following", strategy.getName());
        assertEquals(riskParameters, strategy.getRiskParameters());
        assertEquals("Daily momentum strategy", strategy.getDescription());
    }

    @Test
    void riskParametersValidateExpectedRanges() {
        assertThrows(IllegalArgumentException.class, () -> new RiskParameters(BigDecimal.ZERO, new BigDecimal("4"), new BigDecimal("10")));
        assertThrows(IllegalArgumentException.class, () -> new RiskParameters(new BigDecimal("2"), new BigDecimal("0"), new BigDecimal("10")));
        assertThrows(IllegalArgumentException.class, () -> new RiskParameters(new BigDecimal("2"), new BigDecimal("4"), new BigDecimal("0")));
        assertThrows(IllegalArgumentException.class, () -> new RiskParameters(new BigDecimal("51"), new BigDecimal("4"), new BigDecimal("10")));
        assertThrows(IllegalArgumentException.class, () -> new RiskParameters(new BigDecimal("2"), new BigDecimal("101"), new BigDecimal("10")));
    }

    @Test
    void strategyRejectsMissingRequiredFields() {
        RiskParameters riskParameters = new RiskParameters(new BigDecimal("2"), new BigDecimal("4"), new BigDecimal("10"));
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThrows(IllegalArgumentException.class, () -> new Strategy(null, null, "name", null, riskParameters, true, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Strategy(null, userId, "   ", null, riskParameters, true, null, null));
        assertThrows(NullPointerException.class, () -> new Strategy(null, userId, "name", null, null, true, null, null));
    }
}
