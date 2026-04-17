package com.tradingsaas.tradingcore.domain.model.backtest;

import java.time.Instant;

public record OhlcvBar(
        Instant timestamp,
        double open,
        double high,
        double low,
        double close,
        long volume
) {
    public OhlcvBar {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp must not be null");
        }
        if (high < low) {
            throw new IllegalArgumentException("High must be greater than or equal to low");
        }
        if (open < 0 || high < 0 || low < 0 || close < 0 || volume < 0) {
            throw new IllegalArgumentException("OHLCV values must not be negative");
        }
    }
}
