package com.tradingsaas.tradingcore.domain.model.backtest;

public record BenchmarkComparison(
        double strategyReturn,
        double benchmarkReturn,
        double relativePerformance,
        double alpha,
        double beta
) {
}
