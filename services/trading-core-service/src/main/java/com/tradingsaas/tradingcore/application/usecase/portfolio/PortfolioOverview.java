package com.tradingsaas.tradingcore.application.usecase.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PortfolioOverview(
        UUID userId,
        BigDecimal totalCapital,
        BigDecimal cash,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal equity,
        Double winRate,
        String dataSource,
        List<PortfolioHoldingOverview> holdings,
        List<PortfolioClosedPositionOverview> closedPositions
) {

    public static PortfolioOverview empty(UUID userId, boolean hasPortfolio) {
        return new PortfolioOverview(
                userId,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                null,
                hasPortfolio ? "none" : "missing-portfolio",
                List.of(),
                List.of()
        );
    }
}
