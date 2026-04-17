package com.tradingsaas.tradingcore.domain.model.backtest;

import java.util.List;

public record BacktestResult(
        BacktestMetrics metrics,
        List<BacktestTrade> trades,
        PortfolioSnapshot finalSnapshot
) {
}
