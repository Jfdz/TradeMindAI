package com.tradingsaas.tradingcore.domain.model.backtest;

import java.math.BigDecimal;

public record BacktestTrade(
        String symbol,
        BigDecimal pnl
) {
    public BacktestTrade {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        if (pnl == null) {
            throw new IllegalArgumentException("PnL must not be null");
        }
    }
}
