package com.tradingsaas.marketdata.domain.service;

import com.tradingsaas.marketdata.domain.model.OHLCV;
import com.tradingsaas.marketdata.domain.model.StockPrice;
import com.tradingsaas.marketdata.domain.model.Symbol;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicator;
import com.tradingsaas.marketdata.domain.model.TechnicalIndicatorType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;

public class Ta4jTechnicalIndicatorCalculator implements TechnicalIndicatorCalculator {

    private static final int RSI_PERIOD = 14;
    private static final int MACD_FAST_PERIOD = 12;
    private static final int MACD_SLOW_PERIOD = 26;
    private static final int MACD_SIGNAL_PERIOD = 9;
    private static final int SMA_20_PERIOD = 20;
    private static final int SMA_50_PERIOD = 50;

    @Override
    public List<TechnicalIndicator> calculateLatestIndicators(Symbol symbol, List<StockPrice> stockPrices) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(stockPrices, "stockPrices must not be null");
        if (stockPrices.isEmpty()) {
            throw new IllegalArgumentException("stockPrices must not be empty");
        }

        BarSeries series = toBarSeries(symbol, stockPrices);
        int lastIndex = series.getEndIndex();

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, RSI_PERIOD);
        MACDIndicator macd = new MACDIndicator(closePrice, MACD_FAST_PERIOD, MACD_SLOW_PERIOD);
        EMAIndicator macdSignal = new EMAIndicator(macd, MACD_SIGNAL_PERIOD);
        SMAIndicator sma20 = new SMAIndicator(closePrice, SMA_20_PERIOD);
        SMAIndicator sma50 = new SMAIndicator(closePrice, SMA_50_PERIOD);

        return List.of(
                indicator(symbol, series, lastIndex, TechnicalIndicatorType.RSI, rsi.getValue(lastIndex), Map.of("period", String.valueOf(RSI_PERIOD))),
                indicator(
                        symbol,
                        series,
                        lastIndex,
                        TechnicalIndicatorType.MACD,
                        macd.getValue(lastIndex),
                        Map.of("fastPeriod", String.valueOf(MACD_FAST_PERIOD), "slowPeriod", String.valueOf(MACD_SLOW_PERIOD))),
                indicator(
                        symbol,
                        series,
                        lastIndex,
                        TechnicalIndicatorType.MACD_SIGNAL,
                        macdSignal.getValue(lastIndex),
                        Map.of(
                                "fastPeriod",
                                String.valueOf(MACD_FAST_PERIOD),
                                "slowPeriod",
                                String.valueOf(MACD_SLOW_PERIOD),
                                "signalPeriod",
                                String.valueOf(MACD_SIGNAL_PERIOD))),
                indicator(
                        symbol,
                        series,
                        lastIndex,
                        TechnicalIndicatorType.MACD_HISTOGRAM,
                        macd.getValue(lastIndex).minus(macdSignal.getValue(lastIndex)),
                        Map.of(
                                "fastPeriod",
                                String.valueOf(MACD_FAST_PERIOD),
                                "slowPeriod",
                                String.valueOf(MACD_SLOW_PERIOD),
                                "signalPeriod",
                                String.valueOf(MACD_SIGNAL_PERIOD))),
                indicator(symbol, series, lastIndex, TechnicalIndicatorType.SMA_20, sma20.getValue(lastIndex), Map.of("period", String.valueOf(SMA_20_PERIOD))),
                indicator(symbol, series, lastIndex, TechnicalIndicatorType.SMA_50, sma50.getValue(lastIndex), Map.of("period", String.valueOf(SMA_50_PERIOD))));
    }

    private static BarSeries toBarSeries(Symbol symbol, List<StockPrice> stockPrices) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(symbol.ticker())
                .withNumTypeOf(DecimalNum.class)
                .build();

        stockPrices.stream()
                .sorted(Comparator.comparing(StockPrice::date))
                .map(Ta4jTechnicalIndicatorCalculator::toBar)
                .forEach(series::addBar);

        return series;
    }

    private static BaseBar toBar(StockPrice stockPrice) {
        OHLCV ohlcv = stockPrice.ohlcv();
        ZonedDateTime endTime = stockPrice.date().atStartOfDay(ZoneOffset.UTC);
        return new BaseBar(
                Duration.ofDays(1),
                endTime,
                ohlcv.open().doubleValue(),
                ohlcv.high().doubleValue(),
                ohlcv.low().doubleValue(),
                ohlcv.close().doubleValue(),
                ohlcv.volume());
    }

    private static TechnicalIndicator indicator(
            Symbol symbol,
            BarSeries series,
            int index,
            TechnicalIndicatorType type,
            org.ta4j.core.num.Num value,
            Map<String, String> metadata) {
        return new TechnicalIndicator(symbol, series.getBar(index).getEndTime().toLocalDate(), type, BigDecimal.valueOf(value.doubleValue()), metadata);
    }
}
