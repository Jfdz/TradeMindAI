package com.tradingsaas.tradingcore.domain.model.backtest;

import java.math.BigDecimal;

public record PortfolioPosition(
        String symbol,
        int quantity,
        BigDecimal averageCost,
        BigDecimal lastPrice
) {
}
