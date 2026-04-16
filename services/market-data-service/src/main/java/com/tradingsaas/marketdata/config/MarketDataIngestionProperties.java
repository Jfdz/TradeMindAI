package com.tradingsaas.marketdata.config;

import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-data.ingestion")
public record MarketDataIngestionProperties(
        String weekdayCron,
        ZoneId zone,
        List<TrackedSymbol> trackedSymbols) {

    public List<TrackedSymbol> activeTrackedSymbols() {
        return trackedSymbols == null ? List.of() : trackedSymbols.stream().filter(TrackedSymbol::active).toList();
    }

    public record TrackedSymbol(String ticker, String name, String exchange, boolean active) {}
}
