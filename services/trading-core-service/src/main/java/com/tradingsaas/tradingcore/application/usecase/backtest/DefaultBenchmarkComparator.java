package com.tradingsaas.tradingcore.application.usecase.backtest;

import com.tradingsaas.tradingcore.domain.model.backtest.BenchmarkComparison;
import com.tradingsaas.tradingcore.domain.model.backtest.EquityPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
class DefaultBenchmarkComparator implements BenchmarkComparator {

    @Override
    public BenchmarkComparison compare(List<EquityPoint> strategyCurve, List<EquityPoint> benchmarkCurve) {
        if (strategyCurve == null || strategyCurve.isEmpty() || benchmarkCurve == null || benchmarkCurve.isEmpty()) {
            return new BenchmarkComparison(0, 0, 0, 0, 0);
        }

        List<EquityPoint> orderedStrategy = strategyCurve.stream()
                .sorted(Comparator.comparing(EquityPoint::timestamp))
                .toList();
        List<EquityPoint> orderedBenchmark = benchmarkCurve.stream()
                .sorted(Comparator.comparing(EquityPoint::timestamp))
                .toList();

        int sampleSize = Math.min(orderedStrategy.size(), orderedBenchmark.size());
        List<Double> strategyReturns = new ArrayList<>();
        List<Double> benchmarkReturns = new ArrayList<>();

        for (int index = 1; index < sampleSize; index++) {
            double strategyPrevious = orderedStrategy.get(index - 1).equity().doubleValue();
            double strategyCurrent = orderedStrategy.get(index).equity().doubleValue();
            double benchmarkPrevious = orderedBenchmark.get(index - 1).equity().doubleValue();
            double benchmarkCurrent = orderedBenchmark.get(index).equity().doubleValue();

            if (strategyPrevious > 0.0 && benchmarkPrevious > 0.0) {
                strategyReturns.add((strategyCurrent / strategyPrevious) - 1.0);
                benchmarkReturns.add((benchmarkCurrent / benchmarkPrevious) - 1.0);
            }
        }

        double strategyReturn = totalReturn(orderedStrategy);
        double benchmarkReturn = totalReturn(orderedBenchmark);
        double relativePerformance = strategyReturn - benchmarkReturn;
        double beta = calculateBeta(strategyReturns, benchmarkReturns);
        double alpha = calculateAlpha(strategyReturns, benchmarkReturns, beta);

        return new BenchmarkComparison(strategyReturn, benchmarkReturn, relativePerformance, alpha, beta);
    }

    private double totalReturn(List<EquityPoint> curve) {
        double first = curve.get(0).equity().doubleValue();
        double last = curve.get(curve.size() - 1).equity().doubleValue();
        return first == 0.0 ? 0.0 : (last / first) - 1.0;
    }

    private double calculateBeta(List<Double> strategyReturns, List<Double> benchmarkReturns) {
        if (strategyReturns.isEmpty() || benchmarkReturns.isEmpty()) {
            return 0.0;
        }

        double benchmarkMean = benchmarkReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double strategyMean = strategyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        double covariance = 0.0;
        double variance = 0.0;

        for (int index = 0; index < strategyReturns.size(); index++) {
            double benchmarkDelta = benchmarkReturns.get(index) - benchmarkMean;
            double strategyDelta = strategyReturns.get(index) - strategyMean;
            covariance += strategyDelta * benchmarkDelta;
            variance += benchmarkDelta * benchmarkDelta;
        }

        covariance /= strategyReturns.size();
        variance /= benchmarkReturns.size();

        if (variance == 0.0) {
            return 0.0;
        }

        return covariance / variance;
    }

    private double calculateAlpha(List<Double> strategyReturns, List<Double> benchmarkReturns, double beta) {
        if (strategyReturns.isEmpty() || benchmarkReturns.isEmpty()) {
            return 0.0;
        }

        double strategyMean = strategyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double benchmarkMean = benchmarkReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return strategyMean - (beta * benchmarkMean);
    }
}
