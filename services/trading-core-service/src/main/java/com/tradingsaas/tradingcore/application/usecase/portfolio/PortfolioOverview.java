package com.tradingsaas.tradingcore.application.usecase.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PortfolioOverview(
        UUID userId,
        BigDecimal initialCapital,
        BigDecimal cash,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal equity,
        double winRate,
        List<PortfolioHoldingOverview> holdings
) {

    public static PortfolioOverview empty(UUID userId, BigDecimal initialCapital) {
        return new PortfolioOverview(
                userId,
                initialCapital,
                initialCapital,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                initialCapital,
                0.0,
                List.of()
        );
    }
}
