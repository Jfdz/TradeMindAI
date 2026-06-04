package com.tradingsaas.marketdata.enrichment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market-data.finnhub")
public record FinnhubProperties(
        String baseUrl,
        String apiKey,
        long timeoutSeconds,
        Cache cache) {

    public record Cache(
            Duration profileTtl,
            Duration newsTtl,
            Duration earningsTtl,
            Duration recommendationsTtl,
            Duration peersTtl,
            Duration insiderTtl,
            Duration sentimentTtl) {}
}
