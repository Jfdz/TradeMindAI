package com.tradingsaas.tradingcore.application.usecase.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tradingsaas.tradingcore.domain.model.backtest.BacktestTrade;
import com.tradingsaas.tradingcore.domain.model.backtest.EquityPoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultMetricsCalculatorTest {

    @Test
    void calculateShouldReturnExpectedBacktestMetrics() {
        DefaultMetricsCalculator calculator = new DefaultMetricsCalculator();

        List<EquityPoint> equityCurve = List.of(
                new EquityPoint(Instant.parse("2026-04-15T00:00:00Z"), new BigDecimal("100")),
                new EquityPoint(Instant.parse("2026-04-16T00:00:00Z"), new BigDecimal("110")),
                new EquityPoint(Instant.parse("2026-04-17T00:00:00Z"), new BigDecimal("100"))
        );

        List<BacktestTrade> trades = List.of(
                new BacktestTrade("AAPL", new BigDecimal("500")),
                new BacktestTrade("NVDA", new BigDecimal("-250"))
        );

        var metrics = calculator.calculate(equityCurve, trades);

        assertEquals(0.0, metrics.totalReturn(), 1e-9);
        assertEquals(0.0, metrics.annualizedReturn(), 1e-9);
        assertEquals(0.755929, metrics.sharpeRatio(), 1e-6);
        assertEquals(0.7937253933, metrics.sortinoRatio(), 1e-9);
        assertEquals(-0.0909090909, metrics.maxDrawdown(), 1e-9);
        assertEquals(0.0, metrics.calmarRatio(), 1e-9);
        assertEquals(0.5, metrics.winRate(), 1e-9);
        assertEquals(2.0, metrics.profitFactor(), 1e-9);
    }
}
