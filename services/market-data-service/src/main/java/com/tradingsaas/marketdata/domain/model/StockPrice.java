package com.tradingsaas.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Historical market price bar for a tracked symbol.
 */
public record StockPrice(
        Symbol symbol,
        LocalDate date,
        TimeFrame timeFrame,
        OHLCV ohlcv,
        BigDecimal adjustedClose) {

    public StockPrice {
        symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        date = Objects.requireNonNull(date, "date must not be null");
        timeFrame = Objects.requireNonNull(timeFrame, "timeFrame must not be null");
        ohlcv = Objects.requireNonNull(ohlcv, "ohlcv must not be null");
        adjustedClose = Objects.requireNonNull(adjustedClose, "adjustedClose must not be null");

        if (adjustedClose.signum() < 0) {
            throw new IllegalArgumentException("adjustedClose must not be negative");
        }
    }

    public StockPrice(Symbol symbol, LocalDate date, TimeFrame timeFrame, OHLCV ohlcv) {
        this(symbol, date, timeFrame, ohlcv, ohlcv.close());
    }
}
