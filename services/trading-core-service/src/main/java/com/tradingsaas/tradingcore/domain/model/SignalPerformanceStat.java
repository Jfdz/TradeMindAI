package com.tradingsaas.tradingcore.domain.model;

import java.math.BigDecimal;

/**
 * One aggregated performance row: resolved signals of a given {@code signalType}
 * within a {@code confidenceBand}, with the empirical win rate and average
 * favourable/adverse excursion.
 *
 * @param confidenceBand "HIGH" for confidence &ge; 0.80, otherwise "STANDARD"
 * @param winRate        fraction in [0,1]
 * @param avgMaxProfit   mean signed max_profit fraction (e.g. 0.048 = +4.8%)
 * @param avgMaxDrawdown mean signed max_drawdown fraction (e.g. -0.021 = -2.1%)
 */
public record SignalPerformanceStat(
        SignalType signalType,
        String confidenceBand,
        long sampleSize,
        long wins,
        BigDecimal winRate,
        BigDecimal avgMaxProfit,
        BigDecimal avgMaxDrawdown) {
}
