package com.tradingsaas.tradingcore.application.usecase.portfolio;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioHoldingOverview(
        String symbol,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal lastPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        double allocationPct,
        String status,
        Instant openedAt,
        Instant closedAt
) {}
