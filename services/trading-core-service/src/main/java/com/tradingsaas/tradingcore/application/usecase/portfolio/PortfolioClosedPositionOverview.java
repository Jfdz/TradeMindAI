package com.tradingsaas.tradingcore.application.usecase.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortfolioClosedPositionOverview(
        UUID id,
        String symbol,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal exitPrice,
        BigDecimal fees,
        BigDecimal realizedPnl,
        Instant openedAt,
        Instant closedAt
) {}
