package com.tradingsaas.marketdata.domain.service;

import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.PriceFacts;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.TimeFrame;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

/**
 * Pure domain service: builds a {@link PriceFacts} snapshot from a sorted
 * window of {@link StockPrice} bars. No Spring, no I/O, no clock reads —
 * same input always yields the same output.
 */
public class PriceFactsCalculator {

    public static final int SMA_20_PERIOD = 20;
    public static final int SMA_50_PERIOD = 50;
    public static final int SMA_200_PERIOD = 200;
    static final int RSI_PERIOD = 14;
    static final int MACD_FAST = 12;
    static final int MACD_SLOW = 26;
    static final int MACD_SIGNAL = 9;
    static final int SUPPORT_RESISTANCE_WINDOW = 30;
    static final int FIFTY_TWO_WEEKS_BARS = 252;
    static final int RETURN_SCALE = 4;
    static final int PRICE_SCALE = 6;

    public PriceFacts calculate(String ticker, List<StockPrice> history) {
        Objects.requireNonNull(ticker, "ticker must not be null");
        Objects.requireNonNull(history, "history must not be null");
        if (history.isEmpty()) {
            throw new IllegalArgumentException("history must not be empty");
        }

        List<StockPrice> sorted = history.stream()
                .sorted(Comparator.comparing(StockPrice::date))
                .toList();
        int barsAvailable = sorted.size();
        StockPrice last = sorted.get(barsAvailable - 1);

        BarSeries series = toBarSeries(ticker, sorted);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        int lastIndex = series.getEndIndex();

        BigDecimal close = last.ohlcv().close();
        BigDecimal previousClose = barsAvailable >= 2
                ? sorted.get(barsAvailable - 2).ohlcv().close()
                : null;

        return new PriceFacts(
                ticker,
                TimeFrame.DAILY,
                last.date(),
                barsAvailable,
                close,
                previousClose,
                pctChange(sorted, barsAvailable, 1),
                pctChange(sorted, barsAvailable, 5),
                pctChange(sorted, barsAvailable, 30),
                rollingMax(sorted, FIFTY_TWO_WEEKS_BARS, OHLCV::high),
                rollingMin(sorted, FIFTY_TWO_WEEKS_BARS, OHLCV::low),
                smaIfEnough(closePrice, lastIndex, SMA_20_PERIOD, barsAvailable),
                smaIfEnough(closePrice, lastIndex, SMA_50_PERIOD, barsAvailable),
                smaIfEnough(closePrice, lastIndex, SMA_200_PERIOD, barsAvailable),
                rsiIfEnough(closePrice, lastIndex, barsAvailable),
                macdHistogramIfEnough(closePrice, lastIndex, barsAvailable),
                last.ohlcv().volume(),
                volumeAvgIfEnough(sorted, SMA_20_PERIOD),
                rollingMin(sorted, SUPPORT_RESISTANCE_WINDOW, OHLCV::low),
                rollingMax(sorted, SUPPORT_RESISTANCE_WINDOW, OHLCV::high));
    }

    private static BarSeries toBarSeries(String ticker, List<StockPrice> sorted) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(ticker)
                .withNumTypeOf(DecimalNum.class)
                .build();
        sorted.stream().map(PriceFactsCalculator::toBar).forEach(series::addBar);
        return series;
    }

    private static BaseBar toBar(StockPrice price) {
        OHLCV ohlcv = price.ohlcv();
        ZonedDateTime endTime = price.date().atStartOfDay(ZoneOffset.UTC);
        return new BaseBar(
                Duration.ofDays(1),
                endTime,
                ohlcv.open().doubleValue(),
                ohlcv.high().doubleValue(),
                ohlcv.low().doubleValue(),
                ohlcv.close().doubleValue(),
                ohlcv.volume());
    }

    private static BigDecimal pctChange(List<StockPrice> sorted, int barsAvailable, int lookback) {
        if (barsAvailable <= lookback) {
            return null;
        }
        BigDecimal current = sorted.get(barsAvailable - 1).ohlcv().close();
        BigDecimal past = sorted.get(barsAvailable - 1 - lookback).ohlcv().close();
        if (past.signum() == 0) {
            return null;
        }
        return current.subtract(past)
                .divide(past, RETURN_SCALE + 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(RETURN_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal rollingMax(
            List<StockPrice> sorted, int window, java.util.function.Function<OHLCV, BigDecimal> extractor) {
        int from = Math.max(0, sorted.size() - window);
        return sorted.subList(from, sorted.size()).stream()
                .map(p -> extractor.apply(p.ohlcv()))
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private static BigDecimal rollingMin(
            List<StockPrice> sorted, int window, java.util.function.Function<OHLCV, BigDecimal> extractor) {
        int from = Math.max(0, sorted.size() - window);
        return sorted.subList(from, sorted.size()).stream()
                .map(p -> extractor.apply(p.ohlcv()))
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static BigDecimal smaIfEnough(ClosePriceIndicator close, int lastIndex, int period, int barsAvailable) {
        if (barsAvailable < period) {
            return null;
        }
        return toBigDecimal(new SMAIndicator(close, period).getValue(lastIndex), PRICE_SCALE);
    }

    private static BigDecimal rsiIfEnough(ClosePriceIndicator close, int lastIndex, int barsAvailable) {
        if (barsAvailable < RSI_PERIOD + 1) {
            return null;
        }
        return toBigDecimal(new RSIIndicator(close, RSI_PERIOD).getValue(lastIndex), RETURN_SCALE);
    }

    private static BigDecimal macdHistogramIfEnough(ClosePriceIndicator close, int lastIndex, int barsAvailable) {
        if (barsAvailable < MACD_SLOW + MACD_SIGNAL) {
            return null;
        }
        MACDIndicator macd = new MACDIndicator(close, MACD_FAST, MACD_SLOW);
        EMAIndicator signal = new EMAIndicator(macd, MACD_SIGNAL);
        Num histogram = macd.getValue(lastIndex).minus(signal.getValue(lastIndex));
        return toBigDecimal(histogram, PRICE_SCALE);
    }

    private static BigDecimal volumeAvgIfEnough(List<StockPrice> sorted, int window) {
        if (sorted.size() < window) {
            return null;
        }
        long sum = 0L;
        for (int i = sorted.size() - window; i < sorted.size(); i++) {
            sum += sorted.get(i).ohlcv().volume();
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(window), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal toBigDecimal(Num num, int scale) {
        return BigDecimal.valueOf(num.doubleValue()).setScale(scale, RoundingMode.HALF_UP);
    }
}
