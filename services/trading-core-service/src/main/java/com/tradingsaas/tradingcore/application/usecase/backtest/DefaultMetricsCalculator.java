package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestMetrics;
import com.tradingsaas.tradingcore.domain.model.backtest.BacktestTrade;
import com.tradingsaas.tradingcore.domain.model.backtest.EquityPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class DefaultMetricsCalculator implements MetricsCalculator {

    private static final double TRADING_DAYS_PER_YEAR = 252.0;
    private static final double DAYS_PER_YEAR = 365.0;

    @Override
    public BacktestMetrics calculate(List<EquityPoint> equityCurve, List<BacktestTrade> trades) {
        if (equityCurve == null || equityCurve.isEmpty()) {
            return new BacktestMetrics(0, 0, 0, 0, 0, 0, calculateWinRate(trades), calculateProfitFactor(trades));
        }

        List<EquityPoint> orderedCurve = equityCurve.stream()
                .sorted(Comparator.comparing(EquityPoint::timestamp))
                .toList();

        double firstEquity = orderedCurve.get(0).equity().doubleValue();
        double lastEquity = orderedCurve.get(orderedCurve.size() - 1).equity().doubleValue();
        double totalReturn = firstEquity == 0.0 ? 0.0 : (lastEquity / firstEquity) - 1.0;
        double annualizedReturn = calculateAnnualizedReturn(orderedCurve, totalReturn);

        List<Double> returns = calculateReturns(orderedCurve);
        double meanReturn = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sharpeRatio = calculateSharpe(returns, meanReturn);
        double sortinoRatio = calculateSortino(returns, meanReturn);
        double maxDrawdown = calculateMaxDrawdown(orderedCurve);
        double calmarRatio = maxDrawdown < 0.0 ? annualizedReturn / Math.abs(maxDrawdown) : 0.0;

        return new BacktestMetrics(
                totalReturn,
                annualizedReturn,
                sharpeRatio,
                sortinoRatio,
                maxDrawdown,
                calmarRatio,
                calculateWinRate(trades),
                calculateProfitFactor(trades)
        );
    }

    private double calculateAnnualizedReturn(List<EquityPoint> equityCurve, double totalReturn) {
        long elapsedDays = Math.max(1L, equityCurve.get(0).timestamp().until(equityCurve.get(equityCurve.size() - 1).timestamp(), java.time.temporal.ChronoUnit.DAYS));
        double growthFactor = 1.0 + totalReturn;

        if (growthFactor <= 0.0) {
            return 0.0;
        }

        return Math.pow(growthFactor, DAYS_PER_YEAR / elapsedDays) - 1.0;
    }

    private List<Double> calculateReturns(List<EquityPoint> equityCurve) {
        List<Double> returns = new ArrayList<>();

        for (int index = 1; index < equityCurve.size(); index++) {
            double previous = equityCurve.get(index - 1).equity().doubleValue();
            double current = equityCurve.get(index).equity().doubleValue();

            if (previous > 0.0) {
                returns.add((current / previous) - 1.0);
            }
        }

        return returns;
    }

    private double calculateSharpe(List<Double> returns, double meanReturn) {
        if (returns.isEmpty()) {
            return 0.0;
        }

        double variance = returns.stream()
                .mapToDouble(value -> Math.pow(value - meanReturn, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0.0) {
            return 0.0;
        }

        return (meanReturn / stdDev) * Math.sqrt(TRADING_DAYS_PER_YEAR);
    }

    private double calculateSortino(List<Double> returns, double meanReturn) {
        List<Double> downsideReturns = returns.stream()
                .filter(value -> value < 0.0)
                .toList();

        if (downsideReturns.isEmpty()) {
            return 0.0;
        }

        double downsideVariance = downsideReturns.stream()
                .mapToDouble(value -> Math.pow(value, 2))
                .average()
                .orElse(0.0);
        double downsideDeviation = Math.sqrt(downsideVariance);

        if (downsideDeviation == 0.0) {
            return 0.0;
        }

        return (meanReturn / downsideDeviation) * Math.sqrt(TRADING_DAYS_PER_YEAR);
    }

    private double calculateMaxDrawdown(List<EquityPoint> equityCurve) {
        double peak = equityCurve.get(0).equity().doubleValue();
        double maxDrawdown = 0.0;

        for (EquityPoint point : equityCurve) {
            double equity = point.equity().doubleValue();
            if (equity > peak) {
                peak = equity;
            }

            if (peak > 0.0) {
                double drawdown = (equity / peak) - 1.0;
                if (drawdown < maxDrawdown) {
                    maxDrawdown = drawdown;
                }
            }
        }

        return maxDrawdown;
    }

    private double calculateWinRate(List<BacktestTrade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        long wins = trades.stream()
                .filter(trade -> trade.pnl().doubleValue() > 0.0)
                .count();
        return (double) wins / trades.size();
    }

    private double calculateProfitFactor(List<BacktestTrade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        double gains = trades.stream()
                .mapToDouble(trade -> Math.max(trade.pnl().doubleValue(), 0.0))
                .sum();
        double losses = trades.stream()
                .mapToDouble(trade -> Math.min(trade.pnl().doubleValue(), 0.0))
                .sum();

        if (losses == 0.0) {
            return gains > 0.0 ? Double.POSITIVE_INFINITY : 0.0;
        }

        return gains / Math.abs(losses);
    }
}
