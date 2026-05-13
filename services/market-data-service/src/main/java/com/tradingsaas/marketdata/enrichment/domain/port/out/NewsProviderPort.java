package com.tradingsaas.marketdata.enrichment.domain.port.out;

import com.tradingsaas.marketdata.enrichment.domain.model.NewsItem;
import java.time.Instant;
import java.util.List;

/**
 * Outbound port for any news provider (Finnhub, Yahoo RSS, etc.).
 *
 * <p>Each implementation is registered as a Spring bean and discovered
 * by {@code NewsAggregatorService}. Providers fail loud (throw on
 * upstream errors) — the aggregator catches and degrades gracefully.
 *
 * <p>Ordering is governed by {@link #priority()}: lower number = higher
 * trust. When two providers return the same URL, the higher-priority
 * provider wins the dedupe.
 */
public interface NewsProviderPort {

    /** Stable name used in logs and metrics, e.g. "finnhub", "yahoo-rss". */
    String providerName();

    /**
     * Lower number = higher trust / authority. Convention:
     * <ul>
     *   <li>10 — primary licensed feeds (Finnhub)</li>
     *   <li>20 — secondary public feeds (Yahoo RSS headlines)</li>
     * </ul>
     */
    int priority();

    /** Fetch news for a specific ticker between {@code from} and {@code to}. */
    List<NewsItem> fetchTickerNews(String ticker, Instant from, Instant to, int limit);

    /** Fetch general market news for a category (e.g. "general", "forex"). */
    List<NewsItem> fetchMarketNews(String category, int limit);
}
