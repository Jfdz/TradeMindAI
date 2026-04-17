package com.tradingsaas.tradingcore.domain.model.backtest;

public record BacktestMetrics(
        double totalReturn,
        double annualizedReturn,
        double sharpeRatio,
        double sortinoRatio,
        double maxDrawdown,
        double calmarRatio,
        double winRate,
        double profitFactor
) {
}
