package com.tradingsaas.marketdata.domain.model;

/**
 * Supported market data intervals.
 */
public enum TimeFrame {
    MINUTE_1("1m", true),
    MINUTE_5("5m", true),
    MINUTE_15("15m", true),
    MINUTE_30("30m", true),
    HOUR_1("1h", true),
    DAILY("1d", false),
    WEEKLY("1w", false),
    MONTHLY("1mo", false);

    private final String apiValue;
    private final boolean intraday;

    TimeFrame(String apiValue, boolean intraday) {
        this.apiValue = apiValue;
        this.intraday = intraday;
    }

    public String apiValue() {
        return apiValue;
    }

    public boolean isIntraday() {
        return intraday;
    }
}
