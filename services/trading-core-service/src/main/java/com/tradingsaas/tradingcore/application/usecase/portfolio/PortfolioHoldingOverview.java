package com.tradingsaas.tradingcore.application.usecase.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioHoldingOverview(
        java.util.UUID id,
        String symbol,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        Double allocationPct,
        String status,
        Instant openedAt,
        Instant closedAt,
        String name,
        String sector,
        List<BigDecimal> trend7d
) {}
