package com.tradingsaas.tradingcore.adapter.in.web.dto;

import com.tradingsaas.tradingcore.domain.model.RiskParameters;
import com.tradingsaas.tradingcore.domain.model.Strategy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StrategyResponse(
        UUID id,
        UUID userId,
        String name,
        String description,
        boolean active,
        RiskParametersResponse riskParameters,
        Instant createdAt,
        Instant updatedAt) {

    public static StrategyResponse fromDomain(Strategy strategy) {
        RiskParameters riskParameters = strategy.getRiskParameters();
        return new StrategyResponse(
                strategy.getId(),
                strategy.getUserId(),
                strategy.getName(),
                strategy.getDescription(),
                strategy.isActive(),
                new RiskParametersResponse(
                        riskParameters.getStopLossPct(),
                        riskParameters.getTakeProfitPct(),
                        riskParameters.getMaxPositionPct()),
                strategy.getCreatedAt(),
                strategy.getUpdatedAt());
    }

    public record RiskParametersResponse(
            BigDecimal stopLossPct,
            BigDecimal takeProfitPct,
            BigDecimal maxPositionPct) {}
}
