package com.tradingsaas.marketdata.enrichment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-data.news-aggregator")
public record NewsAggregatorProperties(
        long parallelTimeoutSeconds,
        Duration cacheTtl) {

    public NewsAggregatorProperties {
        if (parallelTimeoutSeconds <= 0) {
            parallelTimeoutSeconds = 5L;
        }
        if (cacheTtl == null) {
            cacheTtl = Duration.ofMinutes(30);
        }
    }
}
