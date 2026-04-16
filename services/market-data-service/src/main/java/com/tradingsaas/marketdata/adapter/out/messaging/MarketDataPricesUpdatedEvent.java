package com.tradingsaas.marketdata.adapter.out.messaging;

import java.time.LocalDate;
import java.util.Objects;

public record MarketDataPricesUpdatedEvent(
        String symbol,
        String timeFrame,
        LocalDate from,
        LocalDate to,
        int count) {

    public MarketDataPricesUpdatedEvent {
        symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        timeFrame = Objects.requireNonNull(timeFrame, "timeFrame must not be null");
        from = Objects.requireNonNull(from, "from must not be null");
        to = Objects.requireNonNull(to, "to must not be null");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }
}
