package com.tradingsaas.marketdata.enrichment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Yahoo Finance RSS news provider.
 *
 * <p>This is intentionally limited to the public RSS feed at
 * {@code /rss/2.0/headline?s={ticker}}. We do not scrape HTML pages
 * or parse full article bodies — only headlines, dates, sources, and
 * source URLs, in line with permitted RSS consumption.
 */
@ConfigurationProperties(prefix = "market-data.yahoo-rss")
public record YahooRssProperties(
        boolean enabled,
        String baseUrl,
        long timeoutSeconds,
        String userAgent) {}
