package com.tradingsaas.tradingcore.application.usecase.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tradingsaas.tradingcore.domain.model.backtest.EquityPoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultBenchmarkComparatorTest {

    @Test
    void compareShouldProduceAlphaBetaAndRelativePerformance() {
        DefaultBenchmarkComparator comparator = new DefaultBenchmarkComparator();

        List<EquityPoint> strategyCurve = List.of(
                new EquityPoint(Instant.parse("2026-04-15T00:00:00Z"), new BigDecimal("100")),
                new EquityPoint(Instant.parse("2026-04-16T00:00:00Z"), new BigDecimal("110")),
                new EquityPoint(Instant.parse("2026-04-17T00:00:00Z"), new BigDecimal("110")),
                new EquityPoint(Instant.parse("2026-04-18T00:00:00Z"), new BigDecimal("121"))
        );

        List<EquityPoint> benchmarkCurve = List.of(
                new EquityPoint(Instant.parse("2026-04-15T00:00:00Z"), new BigDecimal("100")),
                new EquityPoint(Instant.parse("2026-04-16T00:00:00Z"), new BigDecimal("105")),
                new EquityPoint(Instant.parse("2026-04-17T00:00:00Z"), new BigDecimal("105")),
                new EquityPoint(Instant.parse("2026-04-18T00:00:00Z"), new BigDecimal("110.25"))
        );

        var comparison = comparator.compare(strategyCurve, benchmarkCurve);

        assertEquals(0.21, comparison.strategyReturn(), 1e-9);
        assertEquals(0.1025, comparison.benchmarkReturn(), 1e-9);
        assertEquals(0.1075, comparison.relativePerformance(), 1e-9);
        assertEquals(2.0, comparison.beta(), 1e-9);
        assertEquals(0.0, comparison.alpha(), 1e-9);
    }
}
