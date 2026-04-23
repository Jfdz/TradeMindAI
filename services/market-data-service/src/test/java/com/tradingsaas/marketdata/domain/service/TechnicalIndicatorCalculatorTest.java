package com.tradingsaas.marketdata.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TechnicalIndicatorCalculatorTest {

    private final TechnicalIndicatorCalculator calculator = new Ta4jTechnicalIndicatorCalculator();

    @Test
    void calculatesLatestIndicatorsForATrendingSeries() {
        Symbol symbol = new Symbol("AAPL", "Apple Inc.", "NASDAQ");
        List<StockPrice> stockPrices = buildTrendingSeries(symbol, 60);

        List<TechnicalIndicator> indicators = calculator.calculateLatestIndicators(symbol, stockPrices);
        Map<TechnicalIndicatorType, TechnicalIndicator> byType = indexByType(indicators);

        assertEquals(6, indicators.size());
        assertEquals(LocalDate.of(2026, 3, 1), indicators.getFirst().date());
        assertTrue(byType.containsKey(TechnicalIndicatorType.RSI));
        assertTrue(byType.containsKey(TechnicalIndicatorType.MACD));
        assertTrue(byType.containsKey(TechnicalIndicatorType.MACD_SIGNAL));
        assertTrue(byType.containsKey(TechnicalIndicatorType.MACD_HISTOGRAM));
        assertTrue(byType.containsKey(TechnicalIndicatorType.SMA_20));
        assertTrue(byType.containsKey(TechnicalIndicatorType.SMA_50));

        BigDecimal rsi = byType.get(TechnicalIndicatorType.RSI).value();
        assertTrue(rsi.compareTo(BigDecimal.ZERO) >= 0);
        assertTrue(rsi.compareTo(BigDecimal.valueOf(100)) <= 0);
        assertEquals(0, rsi.compareTo(BigDecimal.valueOf(100)));

        assertEquals(new BigDecimal("50.5"), byType.get(TechnicalIndicatorType.SMA_20).value());
        assertEquals(new BigDecimal("35.5"), byType.get(TechnicalIndicatorType.SMA_50).value());
        assertFalse(byType.get(TechnicalIndicatorType.MACD).metadata().isEmpty());
        assertFalse(byType.get(TechnicalIndicatorType.MACD_SIGNAL).metadata().isEmpty());
        assertFalse(byType.get(TechnicalIndicatorType.MACD_HISTOGRAM).metadata().isEmpty());
    }

    @Test
    void rejectsEmptyPriceSeries() {
        Symbol symbol = new Symbol("AAPL", "Apple Inc.", "NASDAQ");

        assertThrows(IllegalArgumentException.class, () -> calculator.calculateLatestIndicators(symbol, List.of()));
    }

    private static Map<TechnicalIndicatorType, TechnicalIndicator> indexByType(List<TechnicalIndicator> indicators) {
        Map<TechnicalIndicatorType, TechnicalIndicator> result = new EnumMap<>(TechnicalIndicatorType.class);
        for (TechnicalIndicator indicator : indicators) {
            result.put(indicator.type(), indicator);
        }
        return result;
    }

    private static List<StockPrice> buildTrendingSeries(Symbol symbol, int days) {
        LocalDate start = LocalDate.of(2026, 1, 1);
        return java.util.stream.IntStream.rangeClosed(1, days)
                .mapToObj(day -> {
                    BigDecimal close = BigDecimal.valueOf(day);
                    OHLCV ohlcv = new OHLCV(
                            close,
                            close.add(BigDecimal.ONE),
                            close.subtract(BigDecimal.ONE).max(BigDecimal.ZERO),
                            close,
                            1_000L + day);
                    return new StockPrice(symbol, start.plusDays(day - 1L), TimeFrame.DAILY, ohlcv);
                })
                .toList();
    }

    private static void assertThrows(Class<IllegalArgumentException> clazz, Runnable action) {
        org.junit.jupiter.api.Assertions.assertThrows(clazz, action::run);
    }
}
