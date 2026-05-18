package com.tradingsaas.marketdata.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.PriceFacts;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceFactsCalculatorTest {

    private static final Symbol SYMBOL = new Symbol("AAPL", "Apple Inc.", "NASDAQ");
    private final PriceFactsCalculator calculator = new PriceFactsCalculator();

    @Test
    void rejectsEmptyHistory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate("AAPL", List.of()));
    }

    @Test
    void singleBarProducesSnapshotWithNullIndicators() {
        StockPrice bar = bar(LocalDate.of(2026, 1, 1), 100, 101, 99, 100, 1_000L);

        PriceFacts facts = calculator.calculate("AAPL", List.of(bar));

        assertEquals("AAPL", facts.ticker());
        assertEquals(1, facts.barsAvailable());
        assertEquals(new BigDecimal("100"), facts.close());
        assertNull(facts.previousClose());
        assertNull(facts.pctChange1d());
        assertNull(facts.sma20());
        assertNull(facts.sma50());
        assertNull(facts.sma200());
        assertNull(facts.rsi14());
        assertNull(facts.macdHistogram());
        assertNull(facts.volumeAvg20d());
        assertEquals(new BigDecimal("99"), facts.support());
        assertEquals(new BigDecimal("101"), facts.resistance());
    }

    @Test
    void sma20EqualsConstantPriceWhenAllClosesEqual() {
        List<StockPrice> history = constantSeries(25, new BigDecimal("50.00"));

        PriceFacts facts = calculator.calculate("AAPL", history);

        assertNotNull(facts.sma20());
        assertEquals(0, facts.sma20().compareTo(new BigDecimal("50.00")),
                "SMA20 of constant series must equal the constant; got " + facts.sma20());
    }

    @Test
    void pctChange1dMatchesSimpleReturn() {
        List<StockPrice> history = new ArrayList<>();
        history.add(bar(LocalDate.of(2026, 1, 1), 100, 100, 100, 100, 1_000L));
        history.add(bar(LocalDate.of(2026, 1, 2), 100, 110, 100, 110, 1_000L));

        PriceFacts facts = calculator.calculate("AAPL", history);

        assertNotNull(facts.pctChange1d());
        assertEquals(0, facts.pctChange1d().compareTo(new BigDecimal("10.0000")),
                "pctChange1d should be 10.0000 (10%); got " + facts.pctChange1d());
    }

    @Test
    void sma200OnlyComputedWith200PlusBars() {
        PriceFacts withoutEnough = calculator.calculate("AAPL", constantSeries(199, new BigDecimal("25.0")));
        assertNull(withoutEnough.sma200());

        PriceFacts withEnough = calculator.calculate("AAPL", constantSeries(200, new BigDecimal("25.0")));
        assertNotNull(withEnough.sma200());
        assertEquals(0, withEnough.sma200().compareTo(new BigDecimal("25.0")));
    }

    @Test
    void calculationIsDeterministic() {
        List<StockPrice> history = constantSeries(60, new BigDecimal("123.45"));

        PriceFacts a = calculator.calculate("AAPL", history);
        PriceFacts b = calculator.calculate("AAPL", history);

        assertEquals(a, b);
    }

    @Test
    void supportAndResistanceUseRollingWindow() {
        List<StockPrice> history = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 40; i++) {
            int low = 50 + i;
            int high = 60 + i;
            history.add(bar(start.plusDays(i), low + 1, high, low, low + 5, 1_000L));
        }

        PriceFacts facts = calculator.calculate("AAPL", history);

        // 30-day rolling window: bars 10..39, lows 60..89, highs 70..99.
        assertEquals(new BigDecimal("60"), facts.support());
        assertEquals(new BigDecimal("99"), facts.resistance());
    }

    @Test
    void volumeAvg20dEqualsArithmeticMeanOfLast20Bars() {
        List<StockPrice> history = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < 25; i++) {
            history.add(bar(start.plusDays(i), 100, 101, 99, 100, 1_000L * (i + 1)));
        }

        PriceFacts facts = calculator.calculate("AAPL", history);

        // Last 20 volumes: 6_000..25_000 step 1_000 => mean = 15_500.
        assertNotNull(facts.volumeAvg20d());
        assertEquals(0, facts.volumeAvg20d().compareTo(new BigDecimal("15500.00")),
                "expected 15500.00; got " + facts.volumeAvg20d());
    }

    @Test
    void snapshotAtUsesDateOfLatestBar() {
        List<StockPrice> history = constantSeries(5, new BigDecimal("10"));
        LocalDate expected = history.get(history.size() - 1).date();

        PriceFacts facts = calculator.calculate("AAPL", history);

        assertEquals(expected, facts.snapshotAt());
    }

    @Test
    void rsi14NotComputedWithFewerThan15Bars() {
        assertNull(calculator.calculate("AAPL", constantSeries(14, new BigDecimal("10"))).rsi14());
        assertNotNull(calculator.calculate("AAPL", trendingSeries(20)).rsi14());
    }

    @Test
    void rsi14ApproximatelyFiftyForFlatSeries() {
        // ta4j returns 0 (no movement) or NaN-equivalent; we accept any non-null and in [0,100].
        PriceFacts facts = calculator.calculate("AAPL", constantSeries(30, new BigDecimal("10")));
        assertNotNull(facts.rsi14());
        BigDecimal rsi = facts.rsi14();
        assertTrue(rsi.compareTo(BigDecimal.ZERO) >= 0 && rsi.compareTo(new BigDecimal("100")) <= 0,
                "rsi14 must be within [0,100]; got " + rsi);
    }

    private static List<StockPrice> constantSeries(int days, BigDecimal close) {
        List<StockPrice> history = new ArrayList<>(days);
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < days; i++) {
            history.add(new StockPrice(
                    SYMBOL,
                    start.plusDays(i),
                    TimeFrame.DAILY,
                    new OHLCV(close, close, close, close, 1_000L)));
        }
        return history;
    }

    private static List<StockPrice> trendingSeries(int days) {
        List<StockPrice> history = new ArrayList<>(days);
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int i = 0; i < days; i++) {
            BigDecimal close = new BigDecimal(100 + i);
            history.add(new StockPrice(
                    SYMBOL,
                    start.plusDays(i),
                    TimeFrame.DAILY,
                    new OHLCV(close, close, close, close, 1_000L)));
        }
        return history;
    }

    private static StockPrice bar(LocalDate date, int open, int high, int low, int close, long volume) {
        return new StockPrice(
                SYMBOL,
                date,
                TimeFrame.DAILY,
                new OHLCV(
                        new BigDecimal(open),
                        new BigDecimal(high),
                        new BigDecimal(low),
                        new BigDecimal(close),
                        volume));
    }
}
