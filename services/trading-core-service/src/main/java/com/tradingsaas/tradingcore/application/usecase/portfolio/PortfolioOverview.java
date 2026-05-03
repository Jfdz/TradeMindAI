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
        double winRate,
        String dataSource,
        List<PortfolioHoldingOverview> holdings
) {

    public static PortfolioOverview empty(UUID userId, boolean hasPortfolio) {
        return new PortfolioOverview(
                userId,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0.0,
                hasPortfolio ? "none" : "missing-portfolio",
                List.of()
        );
    }
}
