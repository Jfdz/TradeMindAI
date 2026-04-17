package com.tradingsaas.tradingcore.domain.model.backtest;

import java.math.BigDecimal;
import java.util.Map;

public record PortfolioSnapshot(
        BigDecimal cash,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal equity,
        Map<String, PortfolioPosition> positions
) {
}
