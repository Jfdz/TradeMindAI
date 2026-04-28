package com.tradingsaas.marketdata.adapter.out.messaging;

import java.util.List;
import java.util.Objects;

/**
 * Shared contract consumed by the AI engine's MarketDataEventConsumer.
 * Payload: {"event":"market-data.prices.updated","symbols":["AAPL",...]}
 */
public record MarketDataPricesUpdatedEvent(
        String event,
        List<String> symbols) {

    public static final String EVENT_TYPE = "market-data.prices.updated";

    public MarketDataPricesUpdatedEvent {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(symbols, "symbols must not be null");
    }

    public static MarketDataPricesUpdatedEvent of(String symbol) {
        return new MarketDataPricesUpdatedEvent(EVENT_TYPE, List.of(symbol));
    }
}
